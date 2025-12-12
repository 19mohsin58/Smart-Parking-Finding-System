package com.example.SPFS.Schedulers;

import com.example.SPFS.Entities.Reservations;
import com.example.SPFS.Repositories.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
public class ReservationCleanupScheduler {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.example.SPFS.Repositories.ParkingLotRepository parkingLotRepository;

    @Autowired
    private com.example.SPFS.Repositories.SlotRepository slotRepository;

    @Scheduled(fixedRate = 60000) // Run every minute
    public void cleanupExpiredReservations() {
        // Find ACTIVE reservations where endTime < now
        List<Reservations> expiredReservations = reservationRepository.findByReservationStatusAndEndTimeBefore(
                "ACTIVE", LocalDateTime.now());

        for (Reservations reservation : expiredReservations) {
            // 1. Mark as COMPLETED
            reservation.setReservationStatus("COMPLETED");
            reservationRepository.save(reservation);

            // 1.5 Sync DB Status (Mark Slot as AVAILABLE)
            try {
                com.example.SPFS.Entities.ParkingLot lot = parkingLotRepository.findById(reservation.getParkingLotId())
                        .orElse(null);

                if (lot != null) {
                    List<com.example.SPFS.Entities.Slot> lotSlots = slotRepository.findAllById(lot.getSlotIds());
                    com.example.SPFS.Entities.Slot dbSlot = lotSlots.stream()
                            .filter(s -> s.getSlotNumber().equals(reservation.getSlotId()))
                            .findFirst()
                            .orElse(null);

                    if (dbSlot != null) {
                        dbSlot.setStatus("AVAILABLE");
                        slotRepository.save(dbSlot);
                    }
                }
            } catch (Exception e) {
                System.err.println("Scheduler Error updating Slot status: " + e.getMessage());
            }

            // 2. Return slot to Redis Set
            String slotKey = "lot:slots:" + reservation.getParkingLotId();
            redisTemplate.opsForSet().add(slotKey, reservation.getSlotId());

            // 3. IMPORTANT: Release User Lock (Critical fix based on prev discussion)
            String userLockKey = "user:active_booking:" + reservation.getUserId();
            redisTemplate.delete(userLockKey);

            System.out.println(
                    "Scheduler: Released slot " + reservation.getSlotId() + " and unlocked user "
                            + reservation.getUserId());
        }
    }
}
