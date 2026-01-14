package unipi.lsmdb.SPFS.Entities;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "users")
public class Users {
    @Id
    private String id;
    private String fullName;
    private String email;
    @JsonIgnore
    private String password; // Stored as Hashed value
    private City city; // Embedded City Object (Lite version)

    public void setCity(City city) {
        if (city != null) {
            // Create a "lite" version of the city to avoid embedding heavy data like
            // parkingLotIds
            City liteCity = new City();
            liteCity.setId(city.getId());
            liteCity.setCityName(city.getCityName());
            liteCity.setState(city.getState());
            liteCity.setCountry(city.getCountry());
            // Do NOT copy parkingLotIds
            this.city = liteCity;
        } else {
            this.city = null;
        }
    }

    private String role; // "USER" or "ADMIN"

    private boolean isVerified = false; // Default to false
    @JsonIgnore
    private String verificationCode;
    @JsonIgnore
    private String passwordResetCode;
}
