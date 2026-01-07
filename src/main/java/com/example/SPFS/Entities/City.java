package com.example.SPFS.Entities;

import lombok.Data;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.mongodb.core.index.CompoundIndex;

@Data
@CompoundIndex(name = "geo_idx", def = "{'state': 1}")
@Document(collection = "cities")
public class City {
    @Id
    private String id;

    // Indexing is now handled by the Compound Index above.
    private String cityName;
    private String state;
    private String country;

    private List<String> parkingLotIds;
    // Getters and Setters
}
