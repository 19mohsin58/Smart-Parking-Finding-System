package com.example.SPFS.DTO;

import lombok.Data;

@Data
public class VerifyRequestDTO {
    private String email;
    private String code;
}
