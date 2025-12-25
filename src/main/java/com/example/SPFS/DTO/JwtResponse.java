package com.example.SPFS.DTO;

import lombok.Data;

@Data
public class JwtResponse {
    private String id;
    private String token;
    private String email;
    private String role;
    private String fullName;
    private String cityCollectionId;
    private boolean isVerified;

    public JwtResponse(String accessToken, String id, String email, String role, String fullName,
            String cityCollectionId,
            boolean isVerified) {
        this.token = accessToken;
        this.id = id;
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.cityCollectionId = cityCollectionId;
        this.isVerified = isVerified;
    }
}
