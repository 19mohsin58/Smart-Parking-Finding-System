package unipi.lsmdb.SPFS.DTO;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    private String email;
    private String code;
    private String newPassword;
}

