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
    private com.example.SPFS.Repositories.SlotRepository slotRepository;

    @Scheduled(fixedRate = 60000) // Run every minute
    public void cleanupExpiredReservations() {
        // Find ACTIVE reservations where endTime < now
        List<Reservations> expiredReservations = reservationRepository
                .findByReservationStatusAndEndTimeBefore("ACTIVE", LocalDateTime.now());

        for (Reservations reservation : expiredReservations) {
            // 1.5 Sync DB Status & 2. Return to Redis
            try {
                // 1. Mark as COMPLETED (Optimistic Update)
                reservation.setReservationStatus("COMPLETED");
                reservationRepository.save(reservation);

                // Find Slot directly by the stored ID
                com.example.SPFS.Entities.Slot dbSlot = null;
                if (reservation.getSlotId() != null) {
                    dbSlot = slotRepository.findById(reservation.getSlotId()).orElse(null);
                }

                if (dbSlot != null) {
                    dbSlot.setStatus("AVAILABLE");
                    slotRepository.save(dbSlot);

                    // 2. Return slot to Redis Set
                    String slotKey = "lot:slots:" + reservation.getParkingLotId();
                    redisTemplate.opsForSet().add(slotKey, dbSlot.getSlotNumber());
                    // System.out.println("DEBUG SCHEDULER: Returned slot " + dbSlot.getSlotNumber()
                    // + " to Redis ("
                    // + slotKey + ").");
                } else {
                    System.err.println("DEBUG SCHEDULER ERROR: Slot not found for reservation: " + reservation.getId()
                            + ", SlotID stored: " + reservation.getSlotId());
                }
            } catch (Exception e) {
                System.err.println("DEBUG SCHEDULER EXCEPTION: " + e.getMessage());
                e.printStackTrace();

                // ROLLBACK: Revert status to ACTIVE so it can be picked up again
                System.out.println("DEBUG SCHEDULER: Rolling back status for reservation " + reservation.getId());
                reservation.setReservationStatus("ACTIVE");
                reservationRepository.save(reservation);
            }

            // 3. Release User Lock
            String userLockKey = "user:active_booking:" + reservation.getUserId();
            redisTemplate.delete(userLockKey);

            System.out.println("Scheduler: Cleanup complete for " + reservation.getId());
        }
    }
}
