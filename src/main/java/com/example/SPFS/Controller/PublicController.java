package com.example.SPFS.Controller;

import com.example.SPFS.Entities.City;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Repositories.UserRepository;
import com.example.SPFS.Repositories.CityRepository;
import com.example.SPFS.Repositories.ParkingLotRepository;
import com.example.SPFS.Entities.ParkingLot;

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
    private com.example.SPFS.Services.EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<Object> registerUser(@RequestBody com.example.SPFS.DTO.RegisterRequestDTO registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity
                    .badRequest()
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: Email already taken!"));
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
        // Use the efficient Compound Index lookup
        if (registerRequest.getCountry() != null && registerRequest.getState() != null
                && registerRequest.getCityName() != null) {
            java.util.List<City> cities = cityRepository.findByCountryAndState(registerRequest.getCountry(),
                    registerRequest.getState());
            City matchedCity = cities.stream()
                    .filter(c -> c.getCityName().equalsIgnoreCase(registerRequest.getCityName()))
                    .findFirst()
                    .orElse(null);

            if (matchedCity != null) {
                user.setCityCollectionId(matchedCity.getId());
            } else {
                // Fallback or Error? For now, we allow null cityId or log warning.
                // Usually means Frontend sent invalid data or admin hasn't imported that city
                // yet.
            }
        }

        Users savedUser = userRepository.save(user);

        // 3. Send Verification Email
        emailService.sendVerificationEmail(user.getEmail(), code);

        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody com.example.SPFS.DTO.VerifyRequestDTO verifyRequest) {
        Users user = userRepository.findByEmail(verifyRequest.getEmail())
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        if (user.isVerified()) {
            return ResponseEntity.ok(new com.example.SPFS.DTO.MessageResponse("User is already verified."));
        }

        if (verifyRequest.getCode() != null && verifyRequest.getCode().equals(user.getVerificationCode())) {
            user.setVerified(true);
            user.setVerificationCode(null); // Clear code after successful verification
            userRepository.save(user);
            return ResponseEntity.ok(new com.example.SPFS.DTO.MessageResponse("Email verified successfully!"));
        } else {
            return ResponseEntity
                    .badRequest()
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: Invalid verification code."));
        }
    }

    // --- DEV API: Verify All Users (Temporary) ---
    @PostMapping("/dev/verify-all")
    public ResponseEntity<String> verifyAllUsers() {
        List<Users> allUsers = userRepository.findAll();
        int count = 0;
        for (Users u : allUsers) {
            if (!u.isVerified()) {
                u.setVerified(true);
                u.setVerificationCode(null);
                userRepository.save(u);
                count++;
            }
        }
        return ResponseEntity.ok("Successfully verified " + count + " users.");
    }

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    // --- Cascading Dropdown APIs ---

    @GetMapping("/countries")
    public ResponseEntity<List<String>> getCountries() {
        // Return distinct countries
        List<String> countries = mongoTemplate.findDistinct(new org.springframework.data.mongodb.core.query.Query(),
                "country", City.class, String.class);
        return ResponseEntity.ok(countries);
    }

    @GetMapping("/states")
    public ResponseEntity<List<String>> getStates(@RequestParam String country) {
        // Return distinct states for the given country
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("country").is(country));
        List<String> states = mongoTemplate.findDistinct(query, "state", City.class, String.class);
        return ResponseEntity.ok(states);
    }

    @GetMapping("/cities")
    public ResponseEntity<List<String>> getCities(@RequestParam(required = false) String country,
            @RequestParam(required = false) String state) {

        // If state/country provided, filter. Else return all cities (legacy/fallback)
        if (country != null && state != null) {
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("country").is(country)
                    .and("state").is(state));
            // We return the actual cityNames, distinctly
            return ResponseEntity.ok(mongoTemplate.findDistinct(query, "cityName", City.class, String.class));
        }

        // Original behavior fallback (not recommended for 20k records but keeping for
        // backward compatibility if needed)
        // But let's optimize to just return distinct cityNames anyway
        return ResponseEntity.ok(mongoTemplate.findDistinct(new org.springframework.data.mongodb.core.query.Query(),
                "cityName", City.class, String.class));
    }

    @PostMapping("/resend-verification-code")
    public ResponseEntity<?> resendVerificationCode(@RequestBody com.example.SPFS.DTO.EmailRequestDTO emailRequest) {
        String email = emailRequest.getEmail();
        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        if (user.isVerified()) {
            return ResponseEntity.ok(new com.example.SPFS.DTO.MessageResponse("User is already verified."));
        }

        // Generate new code or resend existing? Let's generate new to be safe.
        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
        user.setVerificationCode(code);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), code);

        return ResponseEntity.ok(new com.example.SPFS.DTO.MessageResponse("Verification code resent successfully!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody com.example.SPFS.DTO.EmailRequestDTO emailRequest) {
        String email = emailRequest.getEmail();
        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // For security, maybe don't reveal if user exists? But for now, let's be
            // explicit as requested.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        String code = String.valueOf((int) ((Math.random() * 900000) + 100000));
        user.setPasswordResetCode(code);
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), code);

        return ResponseEntity.ok(new com.example.SPFS.DTO.MessageResponse("Password reset code sent to email."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody com.example.SPFS.DTO.ResetPasswordRequestDTO resetRequest) {
        Users user = userRepository.findByEmail(resetRequest.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: User not found."));
        }

        if (resetRequest.getCode() == null || !resetRequest.getCode().equals(user.getPasswordResetCode())) {
            return ResponseEntity.badRequest()
                    .body(new com.example.SPFS.DTO.MessageResponse("Error: Invalid reset code."));
        }

        user.setPassword(passwordEncoder.encode(resetRequest.getNewPassword()));
        user.setPasswordResetCode(null); // Clear the code
        userRepository.save(user);

        return ResponseEntity.ok(new com.example.SPFS.DTO.MessageResponse("Password reset successfully!"));
    }

    @GetMapping("/cities/{cityName}/parking-lots")
    public ResponseEntity<List<ParkingLot>> getParkingLotsByCity(@PathVariable String cityName) {
        City city = cityRepository.findByCityName(cityName).orElse(null);
        if (city == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (city.getParkingLotIds() == null || city.getParkingLotIds().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<ParkingLot> parkingLots = parkingLotRepository.findAllById(city.getParkingLotIds());

        // Update with live availability from Redis
        for (ParkingLot lot : parkingLots) {
            Long size = redisTemplate.opsForSet().size("lot:slots:" + lot.getId());
            if (size != null) {
                lot.setAvailableSlots(size.intValue());
            }
        }

        return ResponseEntity.ok(parkingLots);
    }

}