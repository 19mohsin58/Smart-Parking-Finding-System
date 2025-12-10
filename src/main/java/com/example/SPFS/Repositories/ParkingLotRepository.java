package com.example.SPFS.Repositories;

import com.example.SPFS.Entities.ParkingLot;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParkingLotRepository extends MongoRepository<ParkingLot, String> {
    boolean existsByParkingName(String parkingName);
}