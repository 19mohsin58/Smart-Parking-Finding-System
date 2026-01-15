package unipi.lsmdb.SPFS.Controller;

import unipi.lsmdb.SPFS.Entities.City;
import unipi.lsmdb.SPFS.Entities.Users;
import unipi.lsmdb.SPFS.Repositories.UserRepository;
import unipi.lsmdb.SPFS.Repositories.CityRepository;
import unipi.lsmdb.SPFS.Repositories.ParkingLotRepository;
import unipi.lsmdb.SPFS.Entities.ParkingLot;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private ParkingLotRepository parkingLotRepository;
    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private unipi.lsmdb.SPFS.Services.EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<Object> registerUser(@RequestBody unipi.lsmdb.SPFS.DTO.RegisterRequestDTO registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity
                    .badRequest()
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: Email already taken!"));
        }

        Users user = new Users();
        user.setFullName(registerRequest.getFullName());
        user.setEmail(registerRequest.getEmail());

        // 1. Hash Password and Assign Default Role
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole("USER");

        // 1.5 Generate Verification Code (6 digits)
        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
        user.setVerificationCode(code);
        user.setVerified(false);

        // 2. Resolve City ID from Selection
        // Use the efficient State Index (US Only)
        if (registerRequest.getState() != null && registerRequest.getCityName() != null) {
            java.util.List<City> cities = cityRepository.findByState(registerRequest.getState());
            City matchedCity = cities.stream()
                    .filter(c -> c.getCityName().equalsIgnoreCase(registerRequest.getCityName()))
                    .findFirst()
                    .orElse(null);

            if (matchedCity != null) {
                user.setCity(matchedCity);
            }
        }

        Users savedUser = userRepository.save(user);

        // 3. Send Verification Email
        emailService.sendVerificationEmail(user.getEmail(), code);

        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody unipi.lsmdb.SPFS.DTO.VerifyRequestDTO verifyRequest) {
        Users user = userRepository.findByEmail(verifyRequest.getEmail())
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        if (user.isVerified()) {
            return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("User is already verified."));
        }

        if (verifyRequest.getCode() != null && verifyRequest.getCode().equals(user.getVerificationCode())) {
            user.setVerified(true);
            user.setVerificationCode(null); // Clear code after successful verification
            userRepository.save(user);
            return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("Email verified successfully!"));
        } else {
            return ResponseEntity
                    .badRequest()
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: Invalid verification code."));
        }
    }

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @GetMapping("/states")
    public ResponseEntity<List<String>> getStates() {
        // Return distinct states
        List<String> states = mongoTemplate.findDistinct(new org.springframework.data.mongodb.core.query.Query(),
                "state", City.class, String.class);
        return ResponseEntity.ok(states);
    }

    @GetMapping("/cities")
    public ResponseEntity<List<unipi.lsmdb.SPFS.DTO.CityDTO>> getCities(
            @RequestParam(required = false) String state) {

        List<City> cities;

        // 1. Filtered Search (Uses 'geo_idx' {state:1})
        if (state != null) {
            cities = cityRepository.findByState(state);
        }
        // 2. Fallback: Return All
        else {
            cities = cityRepository.findAll();
        }

        // Map to DTOs so frontend gets the ID + Name
        List<unipi.lsmdb.SPFS.DTO.CityDTO> dtos = cities.stream().map(c -> {
            unipi.lsmdb.SPFS.DTO.CityDTO dto = new unipi.lsmdb.SPFS.DTO.CityDTO();
            dto.setId(c.getId());
            dto.setCityName(c.getCityName());
            dto.setState(c.getState());
            dto.setCountry(c.getCountry());
            return dto;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/resend-verification-code")
    public ResponseEntity<?> resendVerificationCode(@RequestBody unipi.lsmdb.SPFS.DTO.EmailRequestDTO emailRequest) {
        String email = emailRequest.getEmail();
        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        if (user.isVerified()) {
            return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("User is already verified."));
        }

        // generate the random code new
        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
        user.setVerificationCode(code);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), code);

        return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("Verification code resent successfully!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody unipi.lsmdb.SPFS.DTO.EmailRequestDTO emailRequest) {
        String email = emailRequest.getEmail();
        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
        user.setPasswordResetCode(code);
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), code);

        return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("Password reset code sent to email."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody unipi.lsmdb.SPFS.DTO.ResetPasswordRequestDTO resetRequest) {
        Users user = userRepository.findByEmail(resetRequest.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        if (resetRequest.getCode() == null || !resetRequest.getCode().equals(user.getPasswordResetCode())) {
            return ResponseEntity.badRequest()
                    .body(new unipi.lsmdb.SPFS.DTO.MessageResponse("Error: Invalid reset code."));
        }

        user.setPassword(passwordEncoder.encode(resetRequest.getNewPassword()));
        user.setPasswordResetCode(null); // Clear the code
        userRepository.save(user);

        return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("Password reset successfully!"));
    }

    @GetMapping("/cities/{cityId}/parking-lots")
    public ResponseEntity<List<unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO>> getParkingLotsByCity(
            @PathVariable String cityId) {
        // Precise Lookup (ID based) - Uses Primary Key Index (O(1))
        City city = cityRepository.findById(cityId).orElse(null);

        if (city == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (city.getParkingLotIds() == null || city.getParkingLotIds().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<ParkingLot> parkingLots = parkingLotRepository.findAllById(city.getParkingLotIds());

        // Convert to DTOs
        List<unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO> dtos = parkingLots.stream().map(lot -> {
            unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO dto = new unipi.lsmdb.SPFS.DTO.ParkingLotResponseDTO();
            dto.setId(lot.getId());
            dto.setParkingName(lot.getParkingName());
            dto.setFullAddress(lot.getFullAddress());
            dto.setTotalCapacity(lot.getTotalCapacity());
            dto.setSlotIds(lot.getSlotIds());

            // 1. Sync Redis Status (AP Fallback)
            try {
                Long available = redisTemplate.opsForSet().size("lot:slots:" + lot.getId());
                if (available != null) {
                    dto.setAvailableSlots(available.intValue());
                } else {
                    dto.setAvailableSlots(lot.getAvailableSlots());
                }
            } catch (Exception e) {
                System.err.println("Warning: Redis is unavailable. Serving stale data for lot " + lot.getId());
                dto.setAvailableSlots(lot.getAvailableSlots());
            }

            dto.setCityName(city.getCityName());

            return dto;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

}
