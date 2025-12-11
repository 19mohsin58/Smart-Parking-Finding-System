package com.example.SPFS.Entities;

import lombok.Data;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "users")
public class Users {
    @Id
    private String id;
    private String fullName;
    private String email;
    private String password; // Stored as Hashed value
    private String cityId; // Link to cities collection
    private String role; // "USER" or "ADMIN"
    private List<String> reservationIds;
}