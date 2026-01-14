package unipi.lsmdb.SPFS.DTO;

import lombok.Data;

@Data
public class BookingRequestDTO {
    private String userId;
    private String parkingLotId;
    private String vehicleNumber;
    private int hours;
}

