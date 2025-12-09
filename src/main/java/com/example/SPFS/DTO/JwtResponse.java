package com.example.SPFS.DTO;

import lombok.Data;

@Data
public class JwtResponse {
    private String token;
    private String email;
    private String role;
    private String fullName;
    private String cityId;

    public JwtResponse(String accessToken, String email, String role, String fullName, String cityId) {
        this.token = accessToken;
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.cityId = cityId;
    }
}
