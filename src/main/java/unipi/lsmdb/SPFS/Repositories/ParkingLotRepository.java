package unipi.lsmdb.SPFS.Repositories;

import unipi.lsmdb.SPFS.Entities.ParkingLot;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParkingLotRepository extends MongoRepository<ParkingLot, String> {
    boolean existsByParkingName(String parkingName);

}
