package com.example.SPFS.Entities;

import lombok.Data;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "reservations")
@org.springframework.data.mongodb.core.index.CompoundIndexes({
        @org.springframework.data.mongodb.core.index.CompoundIndex(name = "user_active_idx", def = "{'userId': 1, 'reservationStatus': 1}"),
        @org.springframework.data.mongodb.core.index.CompoundIndex(name = "user_history_idx", def = "{'userId': 1, 'endTime': -1}"),
        @org.springframework.data.mongodb.core.index.CompoundIndex(name = "scheduler_cleanup_idx", def = "{'reservationStatus': 1, 'endTime': 1}")
})
public class Reservations {
    @Id
    private String id;
    private String userId;
    private String parkingLotId;
    private String slotId; // Stores MongoDB _id of the Slot document
    private String vehicleNumber;

    // Using LocalDateTime for easier calculations
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String reservationStatus; // "ACTIVE", "COMPLETED", "CANCELLED"
}
