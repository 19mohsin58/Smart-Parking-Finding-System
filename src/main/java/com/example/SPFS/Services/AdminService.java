package com.example.SPFS.Services;

import com.example.SPFS.Entities.City;
import com.example.SPFS.Entities.ParkingLot;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Repositories.CityRepository;
import com.example.SPFS.Repositories.ParkingLotRepository;
import com.example.SPFS.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    private com.example.SPFS.Repositories.SlotRepository slotRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    // --- CRUD Create: Add Lot and Initialize Redis ---
    public ParkingLot createLot(ParkingLot lot, String cityName, String state, String country) {
        // 0. Constraint: Check if parking lot with same name exists
        if (parkingLotRepository.existsByParkingName(lot.getParkingName())) {
            throw new RuntimeException("Error: Parking lot with name '" + lot.getParkingName() + "' already exists.");
        }

        // 1. Save the new Lot and get its generated ID
        // Ensure the lot object has NO cityId initially.
        lot.setAvailableSlots(lot.getTotalCapacity());
        ParkingLot savedLot = parkingLotRepository.save(lot); // Lot is now in DB with _id

        // 2. Find or Create the City Document (Crucial Linking Step)
        // Optimization: Use the Compound Index (Country -> State)
        java.util.List<City> citiesInState = cityRepository.findByCountryAndState(country, state);

        City city = citiesInState.stream()
                .filter(c -> c.getCityName().equalsIgnoreCase(cityName))
                .findFirst()
                .orElseGet(() -> {
                    // City doesn't exist in this State/Country, create it
                    City newCity = new City();
                    newCity.setCityName(cityName);
                    newCity.setState(state);
                    newCity.setCountry(country);
                    return cityRepository.save(newCity);
                });

        // 3. Update the City document with the new Lot ID (Document Linking)
        // $push the Lot ID into the City's parkingLotIds array
        // 3. Update the City document with the new Lot ID (Document Linking)
        // Replaced mongoTemplate $push with repository.save() to handle schema mismatch
        // (String vs Array)
        if (city.getParkingLotIds() == null) {
            city.setParkingLotIds(new java.util.ArrayList<>());
        }
        city.getParkingLotIds().add(savedLot.getId());
        cityRepository.save(city); // Overwrites the document, fixing the field type if it was corrupted

        // --- NEW STEP: Automatic Slot Generation ---
        int capacity = savedLot.getTotalCapacity();
        java.util.List<com.example.SPFS.Entities.Slot> newSlots = new java.util.ArrayList<>();
        java.util.List<String> slotIds = new java.util.ArrayList<>();

        for (int i = 1; i <= capacity; i++) {
            com.example.SPFS.Entities.Slot slot = new com.example.SPFS.Entities.Slot();
            slot.setSlotNumber("A-" + i);
            slot.setStatus("AVAILABLE");
            // REMOVED: slot.setParkingLotId(savedLot.getId()) as per user request
            newSlots.add(slot);
        }

        // Save all slots in bulk for performance
        java.util.List<com.example.SPFS.Entities.Slot> savedSlots = slotRepository.saveAll(newSlots);

        // Extract IDs
        String[] slotNumbers = new String[savedSlots.size()];
        for (int i = 0; i < savedSlots.size(); i++) {
            com.example.SPFS.Entities.Slot s = savedSlots.get(i);
            slotIds.add(s.getId());
            slotNumbers[i] = s.getSlotNumber();
        }

        // Update ParkingLot with Slot IDs
        savedLot.setSlotIds(slotIds);
        parkingLotRepository.save(savedLot);
        // -------------------------------------------

        // 5. Initialize Redis Hash (Consistency Check)
        // 5. Initialize Redis Set (Consistency Check)
        redisTemplate.opsForSet().add(
                "lot:slots:" + savedLot.getId(),
                (Object[]) slotNumbers);

        return savedLot;
    }

    // --- CRUD Read: Get All Lots with Merged Status ---
    public List<ParkingLot> getAllLotsWithLiveStatus() {
        List<ParkingLot> lots = parkingLotRepository.findAll();

        // Merge the Redis live count into the MongoDB object structure
        for (ParkingLot lot : lots) {
            Long available = redisTemplate.opsForSet().size("lot:slots:" + lot.getId());

            if (available != null) {
                lot.setAvailableSlots(available.intValue());
                System.out.println("DEBUG: Lot " + lot.getParkingName() + " Live Redis Count: " + available);
            }
        }
        return lots;
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
    private com.example.SPFS.Repositories.ReservationRepository reservationRepository;

    // --- DELETE PARKING LOT (Cascading) ---
    public void deleteParkingLot(String parkingLotId) {
        // 1. Fetch Lot
        ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new RuntimeException("Parking Lot not found"));

        // 2. Delete Slots
        if (lot.getSlotIds() != null && !lot.getSlotIds().isEmpty()) {
            slotRepository.deleteAllById(lot.getSlotIds());
        }

        // 3. Cancel Active Reservations & Release User Locks
        List<com.example.SPFS.Entities.Reservations> activeReservations = reservationRepository
                .findByParkingLotIdAndReservationStatus(parkingLotId, "ACTIVE");

        for (com.example.SPFS.Entities.Reservations res : activeReservations) {
            // Update DB Status
            res.setReservationStatus("CANCELLED");
            reservationRepository.save(res);

            // Release User Lock in Redis
            String userLockKey = "user:active_booking:" + res.getUserId();
            redisTemplate.delete(userLockKey);
        }

        // 4. Update City (Remove Lot ID)
        Query query = new Query(Criteria.where("parkingLotIds").is(parkingLotId));
        Update update = new Update().pull("parkingLotIds", parkingLotId);
        mongoTemplate.updateMulti(query, update, City.class);

        // 5. Delete Redis Key for the Lot
        redisTemplate.delete("lot:slots:" + parkingLotId);

        // 6. Delete Lot
        parkingLotRepository.deleteById(parkingLotId);
    }

    // 1. Average Parking Duration (Arithmetic: $subtract, $divide, $avg) - REPLACES
    // Lookup
    public List<Map> getAverageParkingDuration() {
        Aggregation aggregation = Aggregation.newAggregation(
                // Step 1: Calculate duration in hours (endTime - startTime) / 1000 / 3600
                // Note: In Mongo, subtracting dates gives milliseconds.
                Aggregation.project("parkingLotId")
                        .andExpression("(endTime - startTime) / 3600000").as("durationHours"),

                // Step 2: Group by parking lot and calculate average
                Aggregation.group("parkingLotId").avg("durationHours").as("avgDuration"),

                // Step 3: Sort by longest duration
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "avgDuration"));

        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "reservations", Map.class);
        return results.getMappedResults();
    }

    // 2. Peak Booking Hours (Temporal Extraction: $datePart/project)
    public List<Map> getPeakBookingHours() {
        Aggregation aggregation = Aggregation.newAggregation(
                // Step 1: Extract the hour from startTime
                Aggregation.project()
                        .andExpression("hour(startTime)").as("hourOfDay"),
                // Step 2: Group by the extracted hour
                Aggregation.group("hourOfDay").count().as("count"),
                // Step 3: Sort by hour to show a daily timeline
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id"));

        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "reservations", Map.class);
        return results.getMappedResults();
    }

    // 3. User Loyalty Distribution (Statistical: $bucket - Manual implementation
    // via
    // Conditional)
    public List<Map> getUserLoyaltyDistribution() {
        // Since $bucket can be complex with MongoTemplate, we use a Facet or
        // Conditional
        // Project
        // approach which is clearer for "well-done" segmentation.

        Aggregation aggregation = Aggregation.newAggregation(
                // Step 1: Calculate total bookings per user
                Aggregation.group("userId").count().as("bookingCount"),

                // Step 2: Categorize into Tiers using Conditional Logic ($switch / $cond)
                Aggregation.project("bookingCount")
                        .and(ConditionalOperators.when(Criteria.where("bookingCount").gte(20))
                                .then("GOLD")
                                .otherwise(ConditionalOperators.when(Criteria.where("bookingCount").gte(5))
                                        .then("SILVER")
                                        .otherwise("BRONZE")))
                        .as("loyaltyTier"),

                // Step 3: Group by the new Tier field to get the specific counts
                Aggregation.group("loyaltyTier").count().as("userCount"));

        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "reservations", Map.class);
        return results.getMappedResults();
    }

    // 4. Sync Redis with MongoDB & Fix Schema Data
    public void syncDatabaseToRedis() {
        // --- 1. Fix Schema Corruption (String -> Array) for Cities and ParkingLots ---
        fixCollectionSchema("cities", "parkingLotIds");
        fixCollectionSchema("parking_lots", "slotIds");

        // --- 2. Sync Redis ---
        List<ParkingLot> allLots = parkingLotRepository.findAll();
        for (ParkingLot lot : allLots) {
            String redisKey = "lot:slots:" + lot.getId();

            // Only refill if empty or logic demands. Checking key existence prevents
            // overwriting.
            if (!redisTemplate.hasKey(redisKey) && lot.getSlotIds() != null && !lot.getSlotIds().isEmpty()) {

                // Fetch full slot objects using the list of IDs
                List<com.example.SPFS.Entities.Slot> slots = slotRepository.findAllById(lot.getSlotIds());

                // Filter for AVAILABLE slots
                List<String> availableSlotNumbers = slots.stream()
                        .filter(s -> "AVAILABLE".equals(s.getStatus()))
                        .map(com.example.SPFS.Entities.Slot::getSlotNumber)
                        .toList();

                if (!availableSlotNumbers.isEmpty()) {
                    redisTemplate.opsForSet().add(redisKey, availableSlotNumbers.toArray());
                    System.out.println(
                            "Restored " + availableSlotNumbers.size() + " slots for Lot: " + lot.getParkingName());
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
                            List<String> fixedList = java.util.Arrays.stream(content.split(","))
                                    .map(String::trim)
                                    .map(id -> id.replaceAll("^'|'$", "")) // Remove surrounding single quotes
                                    .filter(id -> !id.isEmpty())
                                    .collect(java.util.stream.Collectors.toList());
                            doc.put(fieldName, fixedList);
                        }
                        collection.replaceOne(new org.bson.Document("_id", doc.get("_id")), doc);
                        System.out.println("Fixed corrupted " + fieldName + " in collection " + collectionName
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