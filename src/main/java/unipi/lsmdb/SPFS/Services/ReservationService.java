package unipi.lsmdb.SPFS.Services;

import unipi.lsmdb.SPFS.Entities.Reservations;
import unipi.lsmdb.SPFS.Repositories.ReservationRepository;
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
    private unipi.lsmdb.SPFS.Repositories.ParkingLotRepository parkingLotRepository;

    @Autowired
    private unipi.lsmdb.SPFS.Repositories.SlotRepository slotRepository;

    public Reservations bookSlot(String userId, String parkingLotId, String vehicleNumber, int hours) {
        // Enforce maximum booking duration
        if (hours > 8) {
            throw new RuntimeException("Maximum booking duration is 8 hours");
        }

        // 0. Enforce Single Active Booking Constraint using Redis
        String userLockKey = "user:active_booking:" + userId;
        // setIfAbsent return true if key was set (lock acquired), false if it already
        // exists
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(userLockKey, "ACTIVE");

        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new RuntimeException("User already has an active booking. Please cancel it before booking again.");
        }

        String slotKey = "lot:slots:" + parkingLotId;
        String slotNumber = null;

        try {
            // 1. Atomic Check and Book from Redis

            Object slotObj = redisTemplate.opsForSet().pop(slotKey);

            if (slotObj == null) {
                throw new RuntimeException("Parking Lot is Full");
            }

            slotNumber = slotObj.toString();

            // 1.5 Sync DB Status (Mark Slot as BOOKED) - FIX for "Available" bug
            unipi.lsmdb.SPFS.Entities.ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                    .orElseThrow(() -> new RuntimeException("Parking Lot not found"));

            // Inefficient but necessary due to schema: Find the specific slot by checking
            // all slots of this lot
            // Ideally, Slot should have parkingLotId, but working with it because we can
            // not use document referencing.
            List<unipi.lsmdb.SPFS.Entities.Slot> lotSlots = slotRepository.findAllById(lot.getSlotIds());
            String finalSlotNumber = slotNumber;
            unipi.lsmdb.SPFS.Entities.Slot dbSlot = lotSlots.stream()
                    .filter(s -> s.getSlotNumber().equals(finalSlotNumber))
                    .findFirst()
                    .orElse(null);

            if (dbSlot == null) {
                throw new RuntimeException("Critical Error: Slot found in Redis but not in Database.");
            }

            // Mark slot as BOOKED
            dbSlot.setStatus("BOOKED");
            slotRepository.save(dbSlot);

            // 1.8 Sync ParkingLot Available Count (For AP Read Fallback)
            if (lot.getAvailableSlots() > 0) {
                lot.setAvailableSlots(lot.getAvailableSlots() - 1);
                parkingLotRepository.save(lot);
            }

            // 2. Create Reservation in DB
            Reservations reservation = new Reservations();
            reservation.setUserId(userId);
            reservation.setParkingLotId(parkingLotId);
            reservation.setSlotId(dbSlot.getId());
            reservation.setVehicleNumber(vehicleNumber);
            reservation.setStartTime(LocalDateTime.now());
            reservation.setEndTime(LocalDateTime.now().plusHours(hours));
            reservation.setReservationStatus("ACTIVE");

            Reservations savedReservation = reservationRepository.save(reservation);

            return savedReservation;

        } catch (Exception e) {
            // ROLLBACK: If anything fails (Lot full, DB error, etc.), release the User Lock
            // AND return the slot if we took one
            if (slotNumber != null) {
                System.out.println(
                        "DEBUG ROLLBACK: Returning slot " + slotNumber + " to Redis due to error: " + e.getMessage());
                redisTemplate.opsForSet().add(slotKey, slotNumber);
            }
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

        // 1. Transaction Start (Try-Catch with Rollback)
        try {
            // 1.1 Mark as CANCELLED (Optimistic Update)
            reservation.setReservationStatus("CANCELLED");
            reservationRepository.save(reservation);

            // 1.5 Sync DB Status (Mark Slot as AVAILABLE)
            // Optimization: Find Slot directly by the stored ID
            unipi.lsmdb.SPFS.Entities.Slot dbSlot = slotRepository.findById(reservation.getSlotId())
                    .orElse(null);

            if (dbSlot != null) {
                dbSlot.setStatus("AVAILABLE");
                slotRepository.save(dbSlot);

                // 1.8 Sync ParkingLot Available Count (For AP Read Fallback)
                unipi.lsmdb.SPFS.Entities.ParkingLot lot = parkingLotRepository.findById(reservation.getParkingLotId())
                        .orElse(null);
                if (lot != null) {
                    // We don't check max capacity here to keep it simple, but in prod we might
                    lot.setAvailableSlots(lot.getAvailableSlots() + 1);
                    parkingLotRepository.save(lot);
                }

                // 2. Return Slot to Redis
                String slotKey = "lot:slots:" + reservation.getParkingLotId();
                redisTemplate.opsForSet().add(slotKey, dbSlot.getSlotNumber());
                System.out
                        .println("DEBUG CANCEL: Returned slot " + dbSlot.getSlotNumber() + " to Redis (" + slotKey
                                + ").");
            } else {
                System.err.println("DEBUG CANCEL ERROR: Slot not found for legacy reservation: " + reservationId);
            }
        } catch (Exception e) {
            System.err.println("DEBUG CANCEL EXCEPTION: " + e.getMessage());
            e.printStackTrace();

            // ROLLBACK: Revert status to ACTIVE so the user can try again
            System.out.println("DEBUG CANCEL: Rolling back status for reservation " + reservation.getId());
            reservation.setReservationStatus("ACTIVE");
            reservationRepository.save(reservation);
            // Re-throw so Controller knows it failed
            throw new RuntimeException("Failed to cancel reservation. Please try again.");
        }

        // 3. Release User Lock (Outside Try-Catch to ensure unlock happens if logic
        // succeeds)
        String userLockKey = "user:active_booking:" + reservation.getUserId();
        redisTemplate.delete(userLockKey);
    }

    public unipi.lsmdb.SPFS.Entities.Reservations getUserActiveReservation(String userId) {
        // Technically, due to our constraint, there should be at most 1 active.
        List<unipi.lsmdb.SPFS.Entities.Reservations> actives = reservationRepository.findByUserIdAndReservationStatus(
                userId,
                "ACTIVE");
        if (actives.isEmpty()) {
            return null;
        }
        return actives.get(0);
    }

    public java.util.List<unipi.lsmdb.SPFS.Entities.Reservations> getUserReservationHistory(String userId) {
        return reservationRepository.findByUserIdAndReservationStatusNotOrderByEndTimeDesc(userId, "ACTIVE");
    }
}

