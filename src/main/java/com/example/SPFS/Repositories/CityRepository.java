package com.example.SPFS.Repositories;

import com.example.SPFS.Entities.City;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;

public interface CityRepository extends MongoRepository<City, String> {
    Optional<City> findByCityName(String cityName);

    public List<City> findAll();
}
