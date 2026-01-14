package unipi.lsmdb.SPFS.Services;

import unipi.lsmdb.SPFS.DTO.UpdateProfileDTO;
import unipi.lsmdb.SPFS.Entities.Users;
import unipi.lsmdb.SPFS.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private unipi.lsmdb.SPFS.Repositories.CityRepository cityRepository;

    public Users updateUser(String userId, UpdateProfileDTO dto) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (dto.getFullName() != null && !dto.getFullName().isEmpty()) {
            user.setFullName(dto.getFullName());
        }

        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            // Optional: Check if email is already taken by another user
            user.setEmail(dto.getEmail());
        }

        if (dto.getCityCollectionId() != null && !dto.getCityCollectionId().isEmpty()) {
            unipi.lsmdb.SPFS.Entities.City city = cityRepository.findById(dto.getCityCollectionId())
                    .orElseThrow(() -> new RuntimeException("City not found"));
            user.setCity(city);
        }

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        return userRepository.save(user);
    }
}

