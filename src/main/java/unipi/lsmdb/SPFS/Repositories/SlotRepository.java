package unipi.lsmdb.SPFS.Repositories;

import unipi.lsmdb.SPFS.Entities.Slot;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SlotRepository extends MongoRepository<Slot, String> {
}

