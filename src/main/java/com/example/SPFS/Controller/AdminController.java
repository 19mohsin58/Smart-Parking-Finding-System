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
    public ResponseEntity<ParkingLot> createParkingLot(@RequestBody ParkingLot lot, @RequestParam String cityName,
            @RequestParam String state) {
        // Creates Lot in Mongo and initializes Redis counter
        ParkingLot savedLot = adminService.createLot(lot, cityName, state);
        return ResponseEntity.ok(savedLot);
    }

    @GetMapping("/lots")
    public ResponseEntity<org.springframework.data.domain.Page<com.example.SPFS.DTO.ParkingLotResponseDTO>> getAllParkingLots(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

        // Reads Mongo data and merges with Redis live status
        org.springframework.data.domain.Page<com.example.SPFS.DTO.ParkingLotResponseDTO> lotsWithLiveStatus = adminService
                .getAllLotsWithLiveStatus(pageable);
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

    @DeleteMapping("/lots/{id}")
    public ResponseEntity<?> deleteParkingLot(@PathVariable String id) {
        try {
            adminService.deleteParkingLot(id);
            return ResponseEntity.ok("Parking Lot deleted successfully (Cascading to Slots, Reservations, and City).");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Bulk Import Helpers ---
    @PostMapping("/sync-redis")
    public ResponseEntity<String> syncRedisData() {
        adminService.syncDatabaseToRedis();
        return ResponseEntity
                .ok("Redis synchronization started. Available slots are being restored for lots missing in Redis.");
    }

}