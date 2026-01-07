package com.example.SPFS.Repositories;

import com.example.SPFS.Entities.City;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CityRepository extends MongoRepository<City, String> {

    // Optimized by geo_idx: {'state': 1}
    List<City> findByState(String state);

    public List<City> findAll();
}
