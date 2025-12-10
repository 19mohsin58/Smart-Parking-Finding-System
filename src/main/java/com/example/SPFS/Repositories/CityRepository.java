package com.example.SPFS.Repositories;

import com.example.SPFS.Entities.City;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CityRepository extends MongoRepository<City, String> {
    Optional<City> findByCityName(String cityName);
}
