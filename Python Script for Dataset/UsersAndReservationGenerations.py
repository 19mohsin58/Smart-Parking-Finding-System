import pandas as pd
import numpy as np
import random
from faker import Faker
import names
from datetime import datetime, timedelta, timezone
import bcrypt 
import os
from bson.objectid import ObjectId 
import ast 

# --- ID and Mapping Functions ---
def generate_object_id():
    """Generates a MongoDB-style ObjectID string."""
    return str(ObjectId())

def create_lot_slot_map(parking_lots_df):
    """
    Parses the embedded slotIds array from the parking_lots_df and creates a mapping 
    from parkingLotId to a list of its actual MongoDB Slot ObjectIDs.
    """
    lot_slot_map = {}
    for _, row in parking_lots_df.iterrows():
        lot_id = row['_id']
        
        # Safely convert the string array of ObjectIDs back to a list
        try:
            slot_mongo_ids = ast.literal_eval(row['slotIds'])
        except (ValueError, TypeError):
            slot_mongo_ids = []

        if slot_mongo_ids:
            lot_slot_map[lot_id] = slot_mongo_ids
            
    return lot_slot_map

# --- Configuration ---
# File paths for existing base collections (must be present)
CITIES_FILE = 'cities_collection_final.csv'
PARKING_LOTS_FILE = 'parking_lots_collection_final.csv'
SLOTS_FILE = 'slots_collection_final.csv'

# Number of synthetic records to generate
NUM_USERS = 100
NUM_RESERVATIONS = 500 

# --- 1. Load Existing IDs (MongoDB ObjectIDs) ---
try:
    # We use the local file names as they were saved in the final directory
    cities_df = pd.read_csv(CITIES_FILE)
    parking_lots_df = pd.read_csv(PARKING_LOTS_FILE)
    slots_df = pd.read_csv(SLOTS_FILE)
except FileNotFoundError as e:
    print(f"Error: Required base collection file not found. Please ensure files are in the current directory. Missing: {e.filename}")
    exit()

# Extract IDs needed for linking
city_ids = cities_df['_id'].tolist()
lot_to_slot_map = create_lot_slot_map(parking_lots_df)
valid_parking_lot_ids = list(lot_to_slot_map.keys()) 

if not valid_parking_lot_ids:
    print("ERROR: No valid parking lots with linked slot ObjectIDs found. Cannot generate reservations.")
    exit()

# --- 2. Generate Users Collection (Applying Constraints) ---
print(f"\nGenerating {NUM_USERS} Users (Role=USER, no reservationIds, no _class)...")

fake = Faker()
user_records = []
hashed_password = bcrypt.hashpw('default_pass'.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

for _ in range(NUM_USERS):
    full_name = names.get_full_name()
    first_name = full_name.split()[0]
    email = f"{first_name.lower()}.{random.randint(1, 99)}@{fake.domain_name()}"

    user_records.append({
        '_id': generate_object_id(),
        'fullName': full_name,
        'email': email,
        'password': hashed_password, 
        'cityId': random.choice(city_ids),
        'role': 'USER', # CONSTRAINT: Role is always USER
    })

users_df = pd.DataFrame(user_records)
user_ids = users_df['_id'].tolist()

# --- 3. Generate Reservations Collection (Applying Constraints) ---
print(f"Generating {NUM_RESERVATIONS} Reservations (Strict linking, max 8h, COMPLETED/CANCELLED only)...")

reservation_records = []
# NOTE: We no longer track user_reservation_map as reservationIds is removed from the User schema

reservation_statuses = ["COMPLETED", "CANCELLED"] # CONSTRAINT: Only two statuses

for i in range(NUM_RESERVATIONS):
    res_id = generate_object_id() 
    
    user_id = random.choice(user_ids) # Included in reservation record
    
    # ENHANCEMENT: Strict Lot-to-Slot Linking
    parking_lot_id = random.choice(valid_parking_lot_ids) # Included in reservation record
    slot_id = random.choice(lot_to_slot_map[parking_lot_id]) # Included in reservation record (MongoDB ObjectID)
    
    # Time Constraints: Duration max 8 hours
    start_time_dt = datetime.now(timezone.utc) - timedelta(days=random.randint(1, 30), hours=random.randint(0, 23))
    duration_hours = random.randint(1, 8) 
    end_time_dt = start_time_dt + timedelta(hours=duration_hours)
    
    start_time_iso = start_time_dt.isoformat().replace('+00:00', '.000+00:00')
    end_time_iso = end_time_dt.isoformat().replace('+00:00', '.000+00:00')
    
    vehicle_number = ''.join(random.choices('ABCDEFGHIJKLMNOPQRSTUVWXYZ', k=3)) + \
                     ''.join(random.choices('0123456789', k=3))
    
    status = random.choice(reservation_statuses)

    reservation_records.append({
        '_id': res_id,
        'userId': user_id,
        'parkingLotId': parking_lot_id,
        'slotId': slot_id, 
        'vehicleNumber': vehicle_number,
        'startTime': start_time_iso,
        'endTime': end_time_iso,
        'reservationStatus': status
    })

reservations_df = pd.DataFrame(reservation_records)

# --- 4. Save Collections to CSV ---
USERS_FILE_OUT = 'users_collection_final_new.csv'
RESERVATIONS_FILE_OUT = 'reservations_collection_final_new.csv'

users_df.to_csv(USERS_FILE_OUT, index=False)
reservations_df.to_csv(RESERVATIONS_FILE_OUT, index=False)

print("\n--- Generation Complete ---")
print(f"File '{USERS_FILE_OUT}' created with {len(users_df)} records.")
print(f"File '{RESERVATIONS_FILE_OUT}' created with {len(reservations_df)} records.")

print("\nUsers Collection Head (Role=USER, no reservationIds, no _class):")
print(users_df.head().to_markdown(index=False))

print("\nReservations Collection Head (Strict Lot/Slot linking):")
print(reservations_df.head().to_markdown(index=False))
