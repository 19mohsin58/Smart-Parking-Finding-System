package unipi.lsmdb.SPFS.Controller;

import unipi.lsmdb.SPFS.DTO.UpdateProfileDTO;
import unipi.lsmdb.SPFS.Entities.Users;
import unipi.lsmdb.SPFS.Services.UserService;
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

        try {
            Users updatedUser = userService.updateUser(userId, payload);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Autowired
    private unipi.lsmdb.SPFS.Repositories.UserRepository userRepository;
    @Autowired
    private unipi.lsmdb.SPFS.Repositories.CityRepository cityRepository;
    @Autowired
    private unipi.lsmdb.SPFS.Repositories.ParkingLotRepository parkingLotRepository;
    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/my-parking-lots")
    public ResponseEntity<java.util.List<unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO>> getMyParkingLots() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Users user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getCity() == null) {
            // Return empty list if no city assigned (or 400 based on preference, but empty
            // list is friendlier)
            return ResponseEntity.ok(java.util.List.of());
        }

        // We must fetch full city to get parkingLotIds because embedded city is 'lite'
        // (no IDs)
        unipi.lsmdb.SPFS.Entities.City city = cityRepository.findById(user.getCity().getId()).orElse(null);
        if (city == null || city.getParkingLotIds() == null || city.getParkingLotIds().isEmpty()) {
            return ResponseEntity.ok(java.util.List.of());
        }

        java.util.List<unipi.lsmdb.SPFS.Entities.ParkingLot> parkingLots = parkingLotRepository
                .findAllById(city.getParkingLotIds());

        // Convert to DTOs
        java.util.List<unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO> dtos = parkingLots.stream().map(lot -> {
            unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO dto = new unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO();
            dto.setId(lot.getId());
            dto.setParkingName(lot.getParkingName());
            dto.setFullAddress(lot.getFullAddress());
            dto.setTotalCapacity(lot.getTotalCapacity());
            dto.setSlotIds(lot.getSlotIds());

            try {
                Long available = redisTemplate.opsForSet().size("lot:slots:" + lot.getId());
                if (available != null) {
                    dto.setAvailableSlots(available.intValue());
                } else {
                    dto.setAvailableSlots(lot.getAvailableSlots());
                }
            } catch (Exception e) {
                System.err.println(
                        "Warning: Redis is unavailable. Serving stale data for user lot " + lot.getId());
                dto.setAvailableSlots(lot.getAvailableSlots());
            }

            dto.setCityName(city.getCityName());

            return dto;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
