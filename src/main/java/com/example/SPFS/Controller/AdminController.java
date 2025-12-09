package com.example.SPFS.Controller;

import com.example.SPFS.Entities.ParkingLot;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Services.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    // --- Parking Lot CRUD ---
    @PostMapping("/lots")
    public ResponseEntity<ParkingLot> createParkingLot(@RequestBody ParkingLot lot) {
        // Creates Lot in Mongo and initializes Redis counter
        ParkingLot savedLot = adminService.createLot(lot);
        return ResponseEntity.ok(savedLot);
    }

    @GetMapping("/lots")
    public ResponseEntity<List<ParkingLot>> getAllParkingLots() {
        // Reads Mongo data and merges with Redis live status
        List<ParkingLot> lotsWithLiveStatus = adminService.getAllLotsWithLiveStatus();
        return ResponseEntity.ok(lotsWithLiveStatus);
    }
    // (Implement PUT/DELETE to complete CRUD)

    // --- User Management (Admin Read Access) ---
    @GetMapping("/users")
    public ResponseEntity<List<Users>> getAllUsers() {
        return ResponseEntity.ok(adminService.findAllUsers());
    }

    @GetMapping("/users/id/{id}")
    public ResponseEntity<Users> getUserById(@PathVariable String id) {
        Optional<Users> user = adminService.findUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/users/email")
    public ResponseEntity<Users> getUserByEmail(@RequestParam String email) {
        Optional<Users> user = adminService.findUserByEmail(email);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}