package com.example.SPFS.Repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.SPFS.Entities.Users;

import java.util.Optional;

public interface UserRepository extends MongoRepository<Users, String> {
    Optional<Users> findByEmail(String email);
}