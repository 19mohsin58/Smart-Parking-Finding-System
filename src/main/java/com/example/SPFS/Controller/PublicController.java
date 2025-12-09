package com.example.SPFS.Controller;

import com.example.SPFS.Entities.Users;
import com.example.SPFS.Repositories.UserRepository;
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

}