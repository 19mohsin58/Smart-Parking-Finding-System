classDiagram
    direction RL
    
    class City {
        +private String id
        +private String cityName
        +private String state
        +private String country
        +private List[String] parkingLotIds
    }
    
    class ParkingLot {
        +String id;
        +String parkingName;
        +String fullAddress;
        +int totalCapacity;
        +int availableSlots;
        +List[String] slotIds;
    }
    
    class Slot {
        +String id
        +String slotNumber
        +String status
    }
    
    class Users {
    	+String id
	    +String fullName
	    +String email
	    +String password
	    +String cityCollectionId
	    +String role
	    +Boolean isVerified = false
	    +String verificationCode
	    +String passwordResetCode
    }

    
    class Reservations {
        +String id
        +String userId
        +String parkingLotId
        +String slotId
        +String vehicleNumber
        +LocalDateTime startTime
        +LocalDateTime endTime
        +String reservationStatus
    }
    
    %% Domain relationships
    City "1" --> "0..*" ParkingLot : contains
    ParkingLot "1" --> "0..*" Slot : contains
    Users "1" --> "0..*" Reservations : books
    Slot "1" --> "0..*" Reservations : reserved by
    
    %% LSMDB annotations
    note for Users "password is stored as Hashed value<br />cityCollectionId links to cities collection<br />role is either 'USER' or 'ADMIN'<br />isVerified defaults to false"
    note for City "CompoundIndex(name = geo_idx, def = {'country': 1, 'state': 1, 'cityName': 1})<br />Indexing is handled by the Compound Index above."
    note for Slot "**Redis TTL caching**<br/>Real-time availability<br />slotNumber example 'A-1'<br />status examples 'AVAILABLE', 'OCCUPIED'"
    note for Reservations "slotId stores MongoDB _id of the Slot document<br />reservationStatus 'ACTIVE', 'COMPLETED', 'CANCELLED'"
