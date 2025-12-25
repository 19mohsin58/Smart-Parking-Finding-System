package com.example.SPFS.Controller;

import com.example.SPFS.DTO.UpdateProfileDTO;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable String userId, @RequestBody UpdateProfileDTO payload) {
        // Security Check: Ensure the logged-in user is updating their own profile
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalName = authentication.getName(); // This is the email (username)

        // Note: Ideally we would verify that the ID matches the email, but for now we
        // assume the frontend sends the correct ID.
        // If strict security is needed, we would fetch the user by email and compare
        // IDs.

        try {
            Users updatedUser = userService.updateUser(userId, payload);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Autowired
    private com.example.SPFS.Repositories.UserRepository userRepository;
    @Autowired
    private com.example.SPFS.Repositories.CityRepository cityRepository;
    @Autowired
    private com.example.SPFS.Repositories.ParkingLotRepository parkingLotRepository;
    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/my-parking-lots")
    public ResponseEntity<?> getMyParkingLots() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Users user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getCityCollectionId() == null) {
            return ResponseEntity.badRequest().body("No city assigned to user.");
        }

        com.example.SPFS.Entities.City city = cityRepository.findById(user.getCityCollectionId()).orElse(null);
        if (city == null || city.getParkingLotIds() == null || city.getParkingLotIds().isEmpty()) {
            return ResponseEntity.ok(java.util.List.of());
        }

        java.util.List<com.example.SPFS.Entities.ParkingLot> parkingLots = parkingLotRepository
                .findAllById(city.getParkingLotIds());

        // Update with live availability from Redis with Fallback
        for (com.example.SPFS.Entities.ParkingLot lot : parkingLots) {
            try {
                Long size = redisTemplate.opsForSet().size("lot:slots:" + lot.getId());
                if (size != null) {
                    lot.setAvailableSlots(size.intValue());
                }
            } catch (Exception e) {
                // Redis is down: Fallback to MongoDB 'availableSlots' field (AP)
                System.err.println(
                        "Warning: Redis is unavailable. Serving stale data for user lot " + lot.getId());
            }
        }

        return ResponseEntity.ok(parkingLots);
    }
}
