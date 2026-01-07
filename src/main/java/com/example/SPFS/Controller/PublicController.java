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

    @PostMapping("/dev/migrate-cities-us")
    public ResponseEntity<String> migrateCitiesToUS() {
        List<City> cities = cityRepository.findAll();
        int count = 0;
        for (City city : cities) {
            city.setCountry("US"); // Hardcode US
            cityRepository.save(city);
            count++;
        }
        return ResponseEntity.ok("Migrated " + count + " cities to 'US'.");
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

    @PostMapping("/dev/verify-all")
    public ResponseEntity<String> verifyAllUsers() {
        // 1. Verify all users
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

        // 2. Data Migration: Embed City object for users having only cityCollectionId
        // We use raw Document to access the old 'cityCollectionId' field which is no
        // longer in the Java Entity
        int migratedCount = 0;
        List<org.bson.Document> userDocs = mongoTemplate.findAll(org.bson.Document.class, "users");
        for (org.bson.Document doc : userDocs) {
            // Check if 'city' is missing but 'cityCollectionId' exists
            if (!doc.containsKey("city") && doc.containsKey("cityCollectionId")) {
                String cityId = doc.getString("cityCollectionId");
                if (cityId != null) {
                    City city = cityRepository.findById(cityId).orElse(null);
                    if (city != null) {
                        // Create Lite City (strip parkingLotIds)
                        City liteCity = new City();
                        liteCity.setId(city.getId());
                        liteCity.setCityName(city.getCityName());
                        liteCity.setState(city.getState());
                        liteCity.setCountry(city.getCountry());
                        // No parking IDs

                        doc.put("city", liteCity); // Embed the lite city object
                        // Optional: doc.remove("cityCollectionId");
                        mongoTemplate.save(doc, "users");
                        migratedCount++;
                    }
                }
            }
        }

        return ResponseEntity
                .ok("Successfully verified " + count + " users. Migrated city for " + migratedCount + " users.");
    }

    @PostMapping("/dev/assign-random-cities")
    public ResponseEntity<String> assignRandomCities() {
        List<City> cities = cityRepository.findAll();
        if (cities.isEmpty()) {
            return ResponseEntity.badRequest().body("No cities found in the database.");
        }

        List<Users> users = userRepository.findAll();
        int updatedCount = 0;

        java.util.Random random = new java.util.Random();

        for (Users user : users) {
            // We assign a random city to ALL users, or just those without one?
            // User Request: "randomly assign the cities to all users now" -> implying
            // everyone.
            City randomCity = cities.get(random.nextInt(cities.size()));

            // setCity handles the "lite" conversion (stripping parkingLotIds)
            user.setCity(randomCity);
            userRepository.save(user);
            updatedCount++;
        }

        return ResponseEntity.ok("Successfully assigned random cities to " + updatedCount + " users.");
    }

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    // --- Cascading Dropdown APIs ---

    @GetMapping("/states")
    public ResponseEntity<List<String>> getStates() {
        // Return distinct states (All are assumed US)
        List<String> states = mongoTemplate.findDistinct(new org.springframework.data.mongodb.core.query.Query(),
                "state", City.class, String.class);
        return ResponseEntity.ok(states);
    }

    @GetMapping("/cities")
    public ResponseEntity<List<com.example.SPFS.DTO.CityDTO>> getCities(
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
        List<com.example.SPFS.DTO.CityDTO> dtos = cities.stream().map(c -> {
            com.example.SPFS.DTO.CityDTO dto = new com.example.SPFS.DTO.CityDTO();
            dto.setId(c.getId());
            dto.setCityName(c.getCityName());
            dto.setState(c.getState());
            dto.setCountry(c.getCountry());
            return dto;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(dtos);
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

        // generate the random code new
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

    @GetMapping("/cities/{cityId}/parking-lots")
    public ResponseEntity<List<com.example.SPFS.DTO.ParkingLotResponseDTO>> getParkingLotsByCity(
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
        List<com.example.SPFS.DTO.ParkingLotResponseDTO> dtos = parkingLots.stream().map(lot -> {
            com.example.SPFS.DTO.ParkingLotResponseDTO dto = new com.example.SPFS.DTO.ParkingLotResponseDTO();
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

            // 2. Populate City Info
            // 2. Populate City Name
            dto.setCityName(city.getCityName());

            return dto;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

}