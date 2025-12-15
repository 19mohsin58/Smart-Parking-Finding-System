package com.example.SPFS.Entities;

import lombok.Data;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Data
@CompoundIndex(name = "geo_idx", def = "{'country': 1, 'state': 1, 'cityName': 1}")
@Document(collection = "cities")
public class City {
    @Id
    private String id;

    // REMOVED unique=true. "Springfield" exists in 30+ states.
    // Indexing is now handled by the Compound Index above.
    private String cityName;
    private String state;
    private String country;

    private List<String> parkingLotIds;
    // Getters and Setters
}
