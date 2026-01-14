package unipi.lsmdb.SPFS.Repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import unipi.lsmdb.SPFS.Entities.Users;

import java.util.Optional;

public interface UserRepository extends MongoRepository<Users, String> {
    Optional<Users> findByEmail(String email);
}
