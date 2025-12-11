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

    @Scheduled(fixedRate = 60000) // Run every minute
    public void cleanupExpiredReservations() {
        // Find ACTIVE reservations where endTime < now
        List<Reservations> expiredReservations = reservationRepository.findByReservationStatusAndEndTimeBefore(
                "ACTIVE", LocalDateTime.now());

        for (Reservations reservation : expiredReservations) {
            // 1. Mark as COMPLETED
            reservation.setReservationStatus("COMPLETED");
            reservationRepository.save(reservation);

            // 2. Return slot to Redis Set
            String slotKey = "lot:slots:" + reservation.getParkingLotId();
            redisTemplate.opsForSet().add(slotKey, reservation.getSlotId());

            System.out.println(
                    "Scheduler: Released slot " + reservation.getSlotId() + " for reservation " + reservation.getId());
        }
    }
}
