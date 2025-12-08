package com.example.SPFS.Entities;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cities")
public class City {
    @Id
    private String id;
    private String cityName;
    private String country;

    private List<String> parkingLotIds;
    // Getters and Setters
}
