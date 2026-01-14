package unipi.lsmdb.SPFS.DTO;

import lombok.Data;

@Data
public class VerifyRequestDTO {
    private String email;
    private String code;
}

