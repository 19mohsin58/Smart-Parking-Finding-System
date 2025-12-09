package com.example.SPFS.Services;

import com.example.SPFS.Entities.ParkingLot;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Repositories.ParkingLotRepository;
import com.example.SPFS.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    // --- CRUD Create: Add Lot and Initialize Redis ---
    public ParkingLot createLot(ParkingLot lot) {
        // 1. Save Lot metadata to MongoDB
        ParkingLot savedLot = parkingLotRepository.save(lot);

        // 2. Initialize Redis Hash (Entity 1: The "One" for live count)
        redisTemplate.opsForHash().put(
                "lot:meta:" + savedLot.getId(),
                "currentAvailable",
                savedLot.getTotalCapacity());

        // (NOTE: Initializing the lot:slots SET would happen here as well)

        return savedLot;
    }

    // --- CRUD Read: Get All Lots with Merged Status ---
    public List<ParkingLot> getAllLotsWithLiveStatus() {
        List<ParkingLot> lots = parkingLotRepository.findAll();

        // Merge the Redis live count into the MongoDB object structure
        for (ParkingLot lot : lots) {
            Object available = redisTemplate.opsForHash().get("lot:meta:" + lot.getId(), "currentAvailable");
            // In a real scenario, this count would be moved to a DTO field before
            // returning.
            // For now, we rely on the controller logic or a DTO conversion.
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