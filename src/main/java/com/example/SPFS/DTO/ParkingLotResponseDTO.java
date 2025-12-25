package com.example.SPFS.DTO;

import lombok.Data;
import java.util.List;

@Data
public class ParkingLotResponseDTO {
    private String id;
    private String parkingName;
    private String fullAddress;
    private int totalCapacity;
    private int availableSlots;
    private List<String> slotIds;
    private CityDTO city;
}
