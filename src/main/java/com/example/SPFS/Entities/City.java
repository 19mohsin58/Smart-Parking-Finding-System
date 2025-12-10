package com.example.SPFS.Entities;

import lombok.Data;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Data
@Document(collection = "cities")
public class City {
    @Id
    private String id;
    @Indexed(unique = true)
    private String cityName;
    private String country;

    private List<String> parkingLotIds;
    // Getters and Setters
}
