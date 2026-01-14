package unipi.lsmdb.SPFS.Entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "slots")
public class Slot {
    @Id
    private String id;
    private String slotNumber; // e.g., "A-1"
    private String status; // e.g., "AVAILABLE", "OCCUPIED"
}

