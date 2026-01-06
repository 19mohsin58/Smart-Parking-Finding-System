graph LR
    %% Definition of Actors
        StandardUser[Standard User]
        Admin[Administrator]
        EmailSys[Email System]

    %% System Boundary
    subgraph Smart_Parking_Finder_System [Smart Parking Finder System]
        %% Use Cases
        UC1(View History)
        UC2(Cancel Reservation)
        UC3(Reserve Spot - Redis Lock)
        UC4(View Live Availability)
        UC5(Search Parking)
        UC6(Update Profile)
        UC7(Register Account)
        UC8(Verify Email - OTP)
        UC9(View System Analytics)
        UC10(Login)
        UC11(Monitor Users)
        UC12(Manage Parking Lots)
        
        %% Analytics Sub-components
        LT(Loyalty Tiers)
        DS(Duration Stats)
        PH(Peak Hours)
    end

    %% Standard User Relationships
    StandardUser --- UC1
    StandardUser --- UC2
    StandardUser --- UC3
    StandardUser --- UC4
    StandardUser --- UC5
    StandardUser --- UC6
    StandardUser --- UC7
    StandardUser --- UC9
    StandardUser --- UC10

    %% Administrator Relationships
    Admin --- UC9
    Admin --- UC10
    Admin --- UC11
    Admin --- UC12

    %% Internal System Logic
    UC7 -.-> UC8
    UC8 --- EmailSys
    
    UC9 --> LT
    UC9 --> DS
    UC9 --> PH

    %% Styling for better visibility
    style Smart_Parking_Finder_System fill:#f9f9f9,stroke:#333,stroke-width:2px
    style Admin fill:#ffffff,stroke:#000000,stroke-width:2px
    style StandardUser fill:#ffffff,stroke:#000000,stroke-width:2px
    style EmailSys fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC1 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC2 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC3 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC4 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC5 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC6 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC7 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC8 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC9 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC10 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC11 fill:#ffffff,stroke:#000000,stroke-width:2px
    style UC12 fill:#ffffff,stroke:#000000,stroke-width:2px
    style LT fill:#ffffff,stroke:#000000,stroke-width:2px
    style DS fill:#ffffff,stroke:#000000,stroke-width:2px
    style PH fill:#ffffff,stroke:#000000,stroke-width:2px
