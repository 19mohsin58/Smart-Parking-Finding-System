package com.example.SPFS.Services;

import com.example.SPFS.Entities.Reservations;
import com.example.SPFS.Repositories.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private com.example.SPFS.Repositories.ParkingLotRepository parkingLotRepository;

    @Autowired
    private com.example.SPFS.Repositories.SlotRepository slotRepository;

    public Reservations bookSlot(String userId, String parkingLotId, String vehicleNumber, int hours) {
        // 0. Enforce Single Active Booking Constraint using Redis
        String userLockKey = "user:active_booking:" + userId;
        // setIfAbsent return true if key was set (lock acquired), false if it already
        // exists
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(userLockKey, "ACTIVE");

        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new RuntimeException("User already has an active booking. Please cancel it before booking again.");
        }

        try {
            // 1. Atomic Check and Book from Redis
            String slotKey = "lot:slots:" + parkingLotId;
            Object slotObj = redisTemplate.opsForSet().pop(slotKey);

            if (slotObj == null) {
                throw new RuntimeException("Parking Lot is Full");
            }

            String slotNumber = slotObj.toString();

            // 1.5 Sync DB Status (Mark Slot as BOOKED) - FIX for "Available" bug
            com.example.SPFS.Entities.ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                    .orElseThrow(() -> new RuntimeException("Parking Lot not found"));

            // Inefficient but necessary due to schema: Find the specific slot by checking
            // all slots of this lot
            // Ideally, Slot should have parkingLotId, but we work with what we have.
            List<com.example.SPFS.Entities.Slot> lotSlots = slotRepository.findAllById(lot.getSlotIds());
            com.example.SPFS.Entities.Slot dbSlot = lotSlots.stream()
                    .filter(s -> s.getSlotNumber().equals(slotNumber))
                    .findFirst()
                    .orElse(null);

            if (dbSlot != null) {
                dbSlot.setStatus("BOOKED");
                slotRepository.save(dbSlot); // Persist status change
            }

            // 2. Create Reservation in DB
            Reservations reservation = new Reservations();
            reservation.setUserId(userId);
            reservation.setParkingLotId(parkingLotId);
            reservation.setSlotId(slotNumber);
            reservation.setVehicleNumber(vehicleNumber);
            reservation.setStartTime(LocalDateTime.now());
            reservation.setEndTime(LocalDateTime.now().plusHours(hours));
            reservation.setReservationStatus("ACTIVE");

            Reservations savedReservation = reservationRepository.save(reservation);

            // 3. Update User (REMOVED)

            return savedReservation;

        } catch (Exception e) {
            // ROLLBACK: If anything fails (Lot full, DB error, etc.), release the User Lock
            redisTemplate.delete(userLockKey);
            throw e;
        }
    }

    public void cancelReservation(String reservationId) {
        Reservations reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!"ACTIVE".equals(reservation.getReservationStatus())) {
            throw new RuntimeException("Reservation is not active");
        }

        // 1. Update DB Status
        reservation.setReservationStatus("CANCELLED");
        reservationRepository.save(reservation);

        // 1.5 Sync DB Status (Mark Slot as AVAILABLE)
        com.example.SPFS.Entities.ParkingLot lot = parkingLotRepository.findById(reservation.getParkingLotId())
                .orElseThrow(() -> new RuntimeException("Parking Lot not found"));

        List<com.example.SPFS.Entities.Slot> lotSlots = slotRepository.findAllById(lot.getSlotIds());
        com.example.SPFS.Entities.Slot dbSlot = lotSlots.stream()
                .filter(s -> s.getSlotNumber().equals(reservation.getSlotId()))
                .findFirst()
                .orElse(null);

        if (dbSlot != null) {
            dbSlot.setStatus("AVAILABLE");
            slotRepository.save(dbSlot);
        }

        // 2. Return Slot to Redis
        String slotKey = "lot:slots:" + reservation.getParkingLotId();
        redisTemplate.opsForSet().add(slotKey, reservation.getSlotId());

        // 3. Release User Lock
        String userLockKey = "user:active_booking:" + reservation.getUserId();
        redisTemplate.delete(userLockKey);
    }

    public com.example.SPFS.Entities.Reservations getUserActiveReservation(String userId) {
        // Technically, due to our constraint, there should be at most 1 active.
        List<com.example.SPFS.Entities.Reservations> actives = reservationRepository.findByUserIdAndReservationStatus(
                userId,
                "ACTIVE");
        if (actives.isEmpty()) {
            return null;
        }
        return actives.get(0);
    }

    public java.util.List<com.example.SPFS.Entities.Reservations> getUserReservationHistory(String userId) {
        return reservationRepository.findByUserIdAndReservationStatusNotOrderByEndTimeDesc(userId, "ACTIVE");
    }
}
