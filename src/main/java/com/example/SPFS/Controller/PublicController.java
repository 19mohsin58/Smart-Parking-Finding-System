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

    @PostMapping("/register")
    public ResponseEntity<Object> registerUser(@RequestBody Users user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return new ResponseEntity<>("Email already taken.", HttpStatus.BAD_REQUEST);
        }

        // 1. Hash Password and Assign Default Role
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");

        Users savedUser = userRepository.save(user);
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }

    @GetMapping("/dashboard")
    public String getUserDashboard() {
        return "Welcome, Basic User! Your data will load here.";
    }

    @GetMapping("/cities")
    public ResponseEntity<List<String>> getAllCities() {
        return ResponseEntity.ok(cityRepository.findAll().stream()
                .map(City::getCityName)
                .toList());
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
        return ResponseEntity.ok(parkingLots);
    }

}