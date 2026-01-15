package unipi.lsmdb.SPFS.Services;

import unipi.lsmdb.SPFS.Entities.City;
import unipi.lsmdb.SPFS.Entities.ParkingLot;
import unipi.lsmdb.SPFS.Entities.Users;
import unipi.lsmdb.SPFS.Repositories.CityRepository;
import unipi.lsmdb.SPFS.Repositories.ParkingLotRepository;
import unipi.lsmdb.SPFS.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminService {

        @Autowired
        private ParkingLotRepository parkingLotRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private RedisTemplate<String, Object> redisTemplate;

        @Autowired
        private CityRepository cityRepository;

        @Autowired
        private unipi.lsmdb.SPFS.Repositories.SlotRepository slotRepository;

        @Autowired
        private MongoTemplate mongoTemplate;

        public ParkingLot createLot(ParkingLot lot, String cityName, String state) {

                if (parkingLotRepository.existsByParkingName(lot.getParkingName())) {
                        throw new RuntimeException(
                                        "Error: Parking lot with name '" + lot.getParkingName() + "' already exists.");
                }

                lot.setAvailableSlots(lot.getTotalCapacity());
                ParkingLot savedLot = parkingLotRepository.save(lot);

                java.util.List<City> citiesInState = cityRepository.findByState(state);

                City city = citiesInState.stream()
                                .filter(c -> c.getCityName().equalsIgnoreCase(cityName))
                                .findFirst()
                                .orElseGet(() -> {
                                        // City doesn't exist, create it
                                        City newCity = new City();
                                        newCity.setCityName(cityName);
                                        newCity.setState(state);
                                        newCity.setCountry("US");
                                        return cityRepository.save(newCity);
                                });

                if (city.getParkingLotIds() == null) {
                        city.setParkingLotIds(new java.util.ArrayList<>());
                }
                city.getParkingLotIds().add(savedLot.getId());
                cityRepository.save(city);

                int capacity = savedLot.getTotalCapacity();
                java.util.List<unipi.lsmdb.SPFS.Entities.Slot> newSlots = new java.util.ArrayList<>();
                java.util.List<String> slotIds = new java.util.ArrayList<>();

                for (int i = 1; i <= capacity; i++) {
                        unipi.lsmdb.SPFS.Entities.Slot slot = new unipi.lsmdb.SPFS.Entities.Slot();
                        slot.setSlotNumber("A-" + i);
                        slot.setStatus("AVAILABLE");
                        newSlots.add(slot);
                }

                java.util.List<unipi.lsmdb.SPFS.Entities.Slot> savedSlots = slotRepository.saveAll(newSlots);

                // Extract IDs
                String[] slotNumbers = new String[savedSlots.size()];
                for (int i = 0; i < savedSlots.size(); i++) {
                        unipi.lsmdb.SPFS.Entities.Slot s = savedSlots.get(i);
                        slotIds.add(s.getId());
                        slotNumbers[i] = s.getSlotNumber();
                }

                // Update ParkingLot with Slot IDs
                savedLot.setSlotIds(slotIds);
                parkingLotRepository.save(savedLot);
                // -------------------------------------------

                // 5. Initialize Redis Set (Consistency Check)
                redisTemplate.opsForSet().add(
                                "lot:slots:" + savedLot.getId(),
                                (Object[]) slotNumbers);

                return savedLot;
        }

        public org.springframework.data.domain.Page<unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO> getAllLotsWithLiveStatus(
                        org.springframework.data.domain.Pageable pageable) {
                org.springframework.data.domain.Page<ParkingLot> page = parkingLotRepository.findAll(pageable);

                // Check Redis Availability Once
                boolean isRedisOnline = false;
                try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                        String response = connection.ping();
                        if ("PONG".equals(response)) {
                                isRedisOnline = true;
                        }
                } catch (Exception e) {
                        System.err.println("Redis is globally unavailable. Skipping individual checks.");
                }

                final boolean redisStatus = isRedisOnline;

                // Convert to DTOs and Merge Data
                java.util.List<unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO> dtos = page.getContent().stream()
                                .map(lot -> {
                                        unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO dto = new unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO();
                                        dto.setId(lot.getId());
                                        dto.setParkingName(lot.getParkingName());
                                        dto.setFullAddress(lot.getFullAddress());
                                        dto.setTotalCapacity(lot.getTotalCapacity());
                                        dto.setSlotIds(lot.getSlotIds());

                                        if (redisStatus) {
                                                try {
                                                        Long available = redisTemplate.opsForSet()
                                                                        .size("lot:slots:" + lot.getId());
                                                        if (available != null) {
                                                                dto.setAvailableSlots(available.intValue());
                                                        } else {
                                                                dto.setAvailableSlots(lot.getAvailableSlots());
                                                        }
                                                } catch (Exception e) {
                                                        System.err.println(
                                                                        "Warning: Redis is unavailable. Serving stale data for admin lot "
                                                                                        + lot.getId());
                                                        dto.setAvailableSlots(lot.getAvailableSlots());
                                                }
                                        } else {
                                                // Redis is down, use MongoDB fallback directly
                                                dto.setAvailableSlots(lot.getAvailableSlots());
                                        }

                                        Query query = new Query(Criteria.where("parkingLotIds").is(lot.getId()));
                                        City city = mongoTemplate.findOne(query, City.class);
                                        if (city != null) {
                                                dto.setCityName(city.getCityName());
                                        }

                                        return dto;
                                }).collect(java.util.stream.Collectors.toList());

                return new org.springframework.data.domain.PageImpl<>(dtos, pageable, page.getTotalElements());
        }

        // --- User Fetching ---
        public List<Users> findAllUsers() {
                return userRepository.findAll();
        }

        public Optional<Users> findUserById(String id) {
                return userRepository.findById(id);
        }

        public Optional<Users> findUserByEmail(String email) {
                return userRepository.findByEmail(email);
        }

        @Autowired
        private unipi.lsmdb.SPFS.Repositories.ReservationRepository reservationRepository;

        public void deleteParkingLot(String parkingLotId) {
                // 1. Fetch Lot
                ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                                .orElseThrow(() -> new RuntimeException("Parking Lot not found"));

                // 2. Delete Slots
                if (lot.getSlotIds() != null && !lot.getSlotIds().isEmpty()) {
                        slotRepository.deleteAllById(lot.getSlotIds());
                }

                List<unipi.lsmdb.SPFS.Entities.Reservations> activeReservations = reservationRepository
                                .findByParkingLotIdAndReservationStatus(parkingLotId, "ACTIVE");

                for (unipi.lsmdb.SPFS.Entities.Reservations res : activeReservations) {
                        res.setReservationStatus("CANCELLED");
                        reservationRepository.save(res);

                        String userLockKey = "user:active_booking:" + res.getUserId();
                        redisTemplate.delete(userLockKey);
                }

                Query query = new Query(Criteria.where("parkingLotIds").is(parkingLotId));
                Update update = new Update().pull("parkingLotIds", parkingLotId);
                mongoTemplate.updateMulti(query, update, City.class);

                redisTemplate.delete("lot:slots:" + parkingLotId);

                parkingLotRepository.deleteById(parkingLotId);
        }

        public List<Map> getAverageParkingDuration() {
                Aggregation aggregation = Aggregation.newAggregation(
                                // Step 1: Filter out invalid entries
                                Aggregation.match(Criteria.where("startTime").exists(true).ne(null)
                                                .and("endTime").exists(true).ne(null)),

                                // Step 2: Sanitize startTime
                                Aggregation.project("parkingLotId", "endTime")
                                                .and(ConditionalOperators.when(
                                                                context -> new org.bson.Document("$eq",
                                                                                java.util.Arrays.asList(
                                                                                                new org.bson.Document(
                                                                                                                "$type",
                                                                                                                "$startTime"),
                                                                                                "string")))
                                                                .then(org.springframework.data.mongodb.core.aggregation.StringOperators.Substr
                                                                                .valueOf("$startTime").substring(0, 19))
                                                                .otherwise("$startTime"))
                                                .as("sanitizedStartTime"),

                                // Step 3: Sanitize endTime (keep sanitizedStartTime)
                                Aggregation.project("parkingLotId", "sanitizedStartTime")
                                                .and(ConditionalOperators.when(
                                                                context -> new org.bson.Document("$eq",
                                                                                java.util.Arrays.asList(
                                                                                                new org.bson.Document(
                                                                                                                "$type",
                                                                                                                "$endTime"),
                                                                                                "string")))
                                                                .then(org.springframework.data.mongodb.core.aggregation.StringOperators.Substr
                                                                                .valueOf("$endTime").substring(0, 19))
                                                                .otherwise("$endTime"))
                                                .as("sanitizedEndTime"),

                                // Step 4: Convert both to Dates
                                Aggregation.project("parkingLotId")
                                                .and(org.springframework.data.mongodb.core.aggregation.ConvertOperators.ToDate
                                                                .toDate("$sanitizedStartTime"))
                                                .as("start")
                                                .and(org.springframework.data.mongodb.core.aggregation.ConvertOperators.ToDate
                                                                .toDate("$sanitizedEndTime"))
                                                .as("end"),

                                // Step 5: Calculate duration in hours
                                Aggregation.project("parkingLotId")
                                                .andExpression("(end - start) / 3600000").as("durationHours"),

                                // Step 6: Group and Average
                                Aggregation.group("parkingLotId").avg("durationHours").as("avgDuration"),

                                // Step 7: Sort
                                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "avgDuration"),

                                // Step 8: Top 10 Limit
                                Aggregation.limit(10));

                AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "reservations", Map.class);
                List<Map> rawResults = results.getMappedResults();

                // 1. Extract IDs (The group result puts the ID in "_id")
                List<String> lotIds = rawResults.stream()
                                .map(m -> (String) m.get("_id"))
                                .filter(id -> id != null)
                                .map(String::trim)
                                .collect(java.util.stream.Collectors.toList());

                // 2. Bulk Fetch ParkingLots
                List<ParkingLot> lots = parkingLotRepository.findAllById(lotIds);

                // 3. Create Lookup Map (ID -> ParkingLot)
                Map<String, ParkingLot> lotMap = lots.stream()
                                .collect(java.util.stream.Collectors.toMap(ParkingLot::getId, lot -> lot));

                // 4. Merge Data
                List<Map> enrichedResults = new java.util.ArrayList<>();
                for (Map original : rawResults) {
                        String id = (String) original.get("_id");
                        if (id != null)
                                id = id.trim();

                        ParkingLot lot = lotMap.get(id);

                        // Create new map to ensure mutability
                        Map<String, Object> enriched = new java.util.HashMap<>(original);

                        // Normalize the ID field for the frontend
                        enriched.put("parkingLotId", id);

                        if (lot != null) {
                                enriched.put("parkingName", lot.getParkingName());
                                enriched.put("fullAddress", lot.getFullAddress());
                        } else {
                                enriched.put("parkingName", "Unknown Lot");
                                enriched.put("fullAddress", "Unknown Address");
                        }
                        enrichedResults.add(enriched);
                }

                return enrichedResults;
        }

        public List<Map> getPeakBookingHours() {
                Aggregation aggregation = Aggregation.newAggregation(
                                // Step 1: Filter out naturally invalid entries
                                Aggregation.match(Criteria.where("startTime").exists(true).ne(null)),

                                // Step 2: Sanitize and Convert
                                // We truncate the string to 19 chars (yyyy-MM-ddTHH:mm:ss) to remove
                                // malformed microseconds/timezones like ".692096.000+00:00"
                                Aggregation.project()
                                                .and(ConditionalOperators.when(
                                                                // Custom expression: { $eq: [ { $type: "$startTime" },
                                                                // "string" ] }
                                                                context -> new org.bson.Document("$eq",
                                                                                java.util.Arrays.asList(
                                                                                                new org.bson.Document(
                                                                                                                "$type",
                                                                                                                "$startTime"),
                                                                                                "string")))
                                                                .then(org.springframework.data.mongodb.core.aggregation.StringOperators.Substr
                                                                                .valueOf("$startTime").substring(0, 19))
                                                                .otherwise("$startTime"))
                                                .as("sanitizedDate"),

                                // Step 3: Convert to actual Date object
                                Aggregation.project()
                                                .and(org.springframework.data.mongodb.core.aggregation.ConvertOperators.ToDate
                                                                .toDate("$sanitizedDate"))
                                                .as("convertedDate"),

                                // Step 4: Extract the hour
                                Aggregation.project()
                                                .andExpression("hour(convertedDate)").as("hourOfDay"),

                                // Step 5: Group and Sort
                                Aggregation.group("hourOfDay").count().as("count"),
                                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id"),

                                // Step 6: Rename _id to hour for cleaner JSON response
                                Aggregation.project("count").and("_id").as("hour").andExclude("_id"));

                AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "reservations", Map.class);
                return results.getMappedResults();
        }

        public List<Map> getUserLoyaltyDistribution() {

                Aggregation aggregation = Aggregation.newAggregation(
                                // Step 1: Calculate total bookings per user
                                Aggregation.group("userId").count().as("bookingCount"),

                                // Step 2: Categorize into Tiers using Conditional Logic ($switch / $cond)
                                Aggregation.project("bookingCount")
                                                .and(ConditionalOperators.when(Criteria.where("bookingCount").gte(20))
                                                                .then("GOLD")
                                                                .otherwise(ConditionalOperators
                                                                                .when(Criteria.where("bookingCount")
                                                                                                .gte(5))
                                                                                .then("SILVER")
                                                                                .otherwise("BRONZE")))
                                                .as("loyaltyTier"),

                                // Step 3: Group by the new Tier field to get the specific counts
                                Aggregation.group("loyaltyTier").count().as("userCount"),

                                // Step 4: Rename _id to loyaltyTier and add Criteria Description
                                Aggregation.project("userCount")
                                                .and("_id").as("loyaltyTier")
                                                .and(ConditionalOperators.when(Criteria.where("_id").is("GOLD"))
                                                                .then("20 or more bookings")
                                                                .otherwise(ConditionalOperators
                                                                                .when(Criteria.where("_id")
                                                                                                .is("SILVER"))
                                                                                .then("5 to 19 bookings")
                                                                                .otherwise("Less than 5 bookings")))
                                                .as("criteria")
                                                .andExclude("_id"));

                AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "reservations", Map.class);
                return results.getMappedResults();
        }

        public void syncDatabaseToRedis() {

                fixCollectionSchema("cities", "parkingLotIds");
                fixCollectionSchema("parking_lots", "slotIds");

                // --- 2. Sync Redis ---
                List<ParkingLot> allLots = parkingLotRepository.findAll();
                for (ParkingLot lot : allLots) {
                        String redisKey = "lot:slots:" + lot.getId();

                        if (!redisTemplate.hasKey(redisKey) && lot.getSlotIds() != null
                                        && !lot.getSlotIds().isEmpty()) {

                                List<unipi.lsmdb.SPFS.Entities.Slot> slots = slotRepository
                                                .findAllById(lot.getSlotIds());

                                List<String> availableSlotNumbers = slots.stream()
                                                .filter(s -> "AVAILABLE".equals(s.getStatus()))
                                                .map(unipi.lsmdb.SPFS.Entities.Slot::getSlotNumber)
                                                .toList();

                                if (!availableSlotNumbers.isEmpty()) {
                                        redisTemplate.opsForSet().add(redisKey, availableSlotNumbers.toArray());
                                        System.out.println(
                                                        "Restored " + availableSlotNumbers.size() + " slots for Lot: "
                                                                        + lot.getParkingName());
                                }
                        }
                }
        }

        private void fixCollectionSchema(String collectionName, String fieldName) {
                try {
                        var collection = mongoTemplate.getCollection(collectionName);
                        for (org.bson.Document doc : collection.find()) {
                                Object fieldValue = doc.get(fieldName);
                                if (fieldValue instanceof String) {
                                        String sVal = (String) fieldValue;
                                        if (sVal.startsWith("[") && sVal.endsWith("]")) {
                                                // Parse stringified array safely
                                                String content = sVal.substring(1, sVal.length() - 1);
                                                if (content.trim().isEmpty()) {
                                                        doc.put(fieldName, java.util.Collections.emptyList());
                                                } else {
                                                        // Split by comma, trim spaces, and remove single quotes 'id'
                                                        List<String> fixedList = java.util.Arrays
                                                                        .stream(content.split(","))
                                                                        .map(String::trim)
                                                                        .map(id -> id.replaceAll("^'|'$", "")) // Remove
                                                                                                               // surrounding
                                                                                                               // single
                                                                                                               // quotes
                                                                        .filter(id -> !id.isEmpty())
                                                                        .collect(java.util.stream.Collectors.toList());
                                                        doc.put(fieldName, fixedList);
                                                }
                                                collection.replaceOne(new org.bson.Document("_id", doc.get("_id")),
                                                                doc);
                                                System.out.println("Fixed corrupted " + fieldName + " in collection "
                                                                + collectionName
                                                                + " for _id: " + doc.get("_id"));
                                        }
                                }
                        }
                } catch (Exception e) {
                        System.err.println("Error fixing schema for " + collectionName + ": " + e.getMessage());
                        e.printStackTrace();
                }
        }
}
