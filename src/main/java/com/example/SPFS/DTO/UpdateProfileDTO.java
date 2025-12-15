package com.example.SPFS.DTO;

import lombok.Data;

@Data
public class UpdateProfileDTO {
    private String fullName;
    private String email;
    private String cityCollectionId;
    private String password;
}
