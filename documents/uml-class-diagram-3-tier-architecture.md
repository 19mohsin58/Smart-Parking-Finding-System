classDiagram
    direction TB
    
    %% CONTROLLERS
    class AuthController {
        +login()
        +register()
        +refreshToken()
    }
    
    class UserController {
        +getProfile()
        +updateProfile()
        +getBookings()
    }
    
    class ReservationController {
        +createBooking()
        +cancelBooking()
        +getAvailableSlots()
    }
    
    class AdminController {
        +manageCities()
        +analytics()
    }
    
    class PublicController {
        +searchParking()
        +getNearbySlots()
    }
    
    %% SERVICES
    class UserService {
        +findByUsername()
        +createUser()
        +updateProfile()
    }
    
    class ParkingService {
        +findAvailableSlots()
        +getParkingByCity()
        +updateSlotStatus()
    }
    
    class ReservationService {
        +createReservation()
        +cancelReservation()
        +checkAvailability()
    }
    
    class AdminService {
        +createCity()
        +getAnalytics()
    }
    
    class EmailService {
        +sendBookingConfirmation()
        +sendPasswordReset()
    }
    
    %% ENTITIES
    class City
    class ParkingLot
    class Slot
    class Users
    class Reservations
    
    %% CONTROLLER -> SERVICE
    AuthController ..> UserService : "@Autowired"
    UserController ..> UserService : "@Autowired"
    ReservationController ..> ReservationService : "@Autowired"
    ReservationController ..> ParkingService : "@Autowired"
    AdminController ..> AdminService : "@Autowired"
    PublicController ..> ParkingService : "@Autowired"
    
    %% SERVICE -> ENTITY (via Repositories)
    UserService ..> Users
    ParkingService ..> ParkingLot
    ParkingService ..> Slot
    ReservationService ..> Reservations
    AdminService ..> City
    
    %% CORRECTED Layer notes
    note for AuthController "JWT Authentication<br/>Role-based access"
    note for ReservationController "Booking workflow<br/>Availability checks"
    note for UserService "Business validation<br/>Redis caching"
    note for ParkingService "**Geospatial queries**<br/>Redis TTL slots"
    note for Entities "**MongoDB + Redis Hybrid**<br/>• MongoDB: geospatial indexing<br/>• Redis: **TTL caching** (slots)<br/>• Sharding: City hierarchy"
