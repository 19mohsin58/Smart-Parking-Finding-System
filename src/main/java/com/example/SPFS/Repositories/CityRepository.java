package com.example.SPFS.Repositories;

import com.example.SPFS.Entities.City;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;

public interface CityRepository extends MongoRepository<City, String> {
    Optional<City> findByCityName(String cityName);

    // Optimized by geo_idx: {'country': 1, 'state': 1, 'cityName': 1}
    List<City> findByCountryAndState(String country, String state);

    // Also optimized by the same index due to 'Prefix Rule'
    List<City> findByCountry(String country);

    public List<City> findAll();
}
