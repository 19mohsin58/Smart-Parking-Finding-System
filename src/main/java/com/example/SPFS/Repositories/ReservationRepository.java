package com.example.SPFS.Repositories;

import com.example.SPFS.Entities.Reservations;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends MongoRepository<Reservations, String> {
    List<Reservations> findByReservationStatusAndEndTimeBefore(String status, LocalDateTime time);

    List<Reservations> findByUserId(String userId);

    // Optimized for "Active" tab
    List<Reservations> findByUserIdAndReservationStatus(String userId, String status);

    // Optimized for "History" tab (Everything NOT active, e.g. Cancelled/Completed,
    // sorted by newest)
    List<Reservations> findByUserIdAndReservationStatusNotOrderByEndTimeDesc(String userId, String status);
}
