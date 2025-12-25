classDiagram
    direction TB
    
    class City {
        +Long cityId (PK)
        +String name
        +String description
    }
    
    class ParkingLot {
        +Long parkingLotId (PK)
        +Long cityId (FK)
        +String name
        +int totalSlots
        +String address
    }
    
    class Slot {
        +Long slotId (PK)
        +Long parkingLotId (FK)
        +int slotNumber
        +String status
        +double pricePerHour
    }
    
    class Users {
        +Long userId (PK)
        +String username
        +String email
        +String password
        +String role
    }
    
    class Reservations {
        +Long reservationId (PK)
        +Long userId (FK)
        +Long slotId (FK)
        +LocalDateTime startTime
        +LocalDateTime endTime
        +String status
    }
    
    %% Domain relationships
    City "1" --> "0..*" ParkingLot : contains
    ParkingLot "1" --> "0..*" Slot : contains
    Users "1" --> "0..*" Reservations : books
    Slot "1" --> "0..*" Reservations : reserved by
    
    %% LSMDB annotations
    note for City "Geospatial indexing<br/>MongoDB 2dsphere"
    note for Slot "**Redis TTL caching**<br/>Real-time availability"
    note for Reservations "**Polyglot Persistence**<br/>MongoDB audit trail"
