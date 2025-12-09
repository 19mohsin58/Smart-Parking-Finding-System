package com.example.SPFS.Entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "parking_lots")
public class ParkingLot {
    @Id
    private String id;
    private String parkingName;
    private String fullAddress;
    private int totalCapacity;
    private String cityId;
    private List<String> slotIds; // Document Linking (Strategy I)
}
