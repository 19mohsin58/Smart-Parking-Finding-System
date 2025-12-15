package com.example.SPFS.Controller;

import com.example.SPFS.Entities.City;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Repositories.UserRepository;
import com.example.SPFS.Repositories.CityRepository;
import com.example.SPFS.Repositories.ParkingLotRepository;
import com.example.SPFS.Entities.ParkingLot;
import org.springframework.data.redis.core.RedisTemplate;

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
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/register")
    public ResponseEntity<Object> registerUser(@RequestBody com.example.SPFS.DTO.RegisterRequestDTO registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return new ResponseEntity<>("Email already taken.", HttpStatus.BAD_REQUEST);
        }

        Users user = new Users();
        user.setFullName(registerRequest.getFullName());
        user.setEmail(registerRequest.getEmail());

        // 1. Hash Password and Assign Default Role
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole("USER");

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
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @GetMapping("/dashboard")
    public String getUserDashboard() {
        return "Welcome, Basic User! Your data will load here.";
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