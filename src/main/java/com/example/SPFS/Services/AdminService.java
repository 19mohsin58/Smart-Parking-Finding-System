package com.example.SPFS.Services;

import com.example.SPFS.Entities.City;
import com.example.SPFS.Entities.ParkingLot;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Repositories.CityRepository;
import com.example.SPFS.Repositories.ParkingLotRepository;
import com.example.SPFS.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
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
    public ParkingLot createLot(ParkingLot lot, String cityName) {
        // 0. Constraint: Check if parking lot with same name exists
        if (parkingLotRepository.existsByParkingName(lot.getParkingName())) {
            throw new RuntimeException("Error: Parking lot with name '" + lot.getParkingName() + "' already exists.");
        }

        // 1. Save the new Lot and get its generated ID
        // Ensure the lot object has NO cityId initially.
        lot.setAvailableSlots(lot.getTotalCapacity());
        ParkingLot savedLot = parkingLotRepository.save(lot); // Lot is now in DB with _id

        // 2. Find or Create the City Document (Crucial Linking Step)
        City city = cityRepository.findByCityName(cityName)
                .orElseGet(() -> {
                    // City doesn't exist, create it with an empty array
                    City newCity = new City();
                    newCity.setCityName(cityName);
                    return cityRepository.save(newCity);
                });

        // 3. Update the City document with the new Lot ID (Document Linking)
        // $push the Lot ID into the City's parkingLotIds array
        Query query = new Query(Criteria.where("id").is(city.getId()));
        Update update = new Update().push("parkingLotIds", savedLot.getId());
        mongoTemplate.updateFirst(query, update, City.class);

        // 4. Update the saved Lot document with the back-reference City ID (REMOVED as
        // per user request)
        // savedLot.setCityId(city.getId());
        // parkingLotRepository.save(savedLot); // Update the Lot with cityId

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
        for (com.example.SPFS.Entities.Slot s : savedSlots) {
            slotIds.add(s.getId());
        }

        // Update ParkingLot with Slot IDs
        savedLot.setSlotIds(slotIds);
        parkingLotRepository.save(savedLot);
        // -------------------------------------------

        // 5. Initialize Redis Hash (Consistency Check)
        redisTemplate.opsForHash().put(
                "lot:meta:" + savedLot.getId(),
                "currentAvailable",
                savedLot.getTotalCapacity());

        return savedLot;
    }

    // --- CRUD Read: Get All Lots with Merged Status ---
    public List<ParkingLot> getAllLotsWithLiveStatus() {
        List<ParkingLot> lots = parkingLotRepository.findAll();

        // Merge the Redis live count into the MongoDB object structure
        for (ParkingLot lot : lots) {
            Object available = redisTemplate.opsForHash().get("lot:meta:" + lot.getId(), "currentAvailable");

            if (available != null) {
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
}