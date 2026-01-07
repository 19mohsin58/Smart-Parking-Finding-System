package com.example.SPFS.DTO;

import lombok.Data;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParkingLotResponseDTO {
    private String id;
    private String parkingName;
    private String fullAddress;
    private int totalCapacity;
    private int availableSlots;
    private List<String> slotIds;
    private String cityName;
}
