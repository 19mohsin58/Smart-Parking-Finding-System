package com.example.SPFS.DTO;

import lombok.Data;

@Data
public class RegisterRequestDTO {
    private String fullName;
    private String email;
    private String password;

    // Geographical Selections
    private String country;
    private String state;
    private String cityName;
}
