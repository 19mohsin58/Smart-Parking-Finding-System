# --- SCRIPT 1: BASE COLLECTIONS GENERATION ---

import pandas as pd
import numpy as np
import os
from bson.objectid import ObjectId 

def generate_object_id():
    """Generates a MongoDB-style ObjectID string."""
    return str(ObjectId())

# Load files 
df1 = pd.read_csv("Public_Garages_and_Parking_Lots_5644684821831453027 (3).csv")
df2 = pd.read_csv("Parking Data.csv")

# --- 1. Process File 1 (Seattle) ---
df_seattle = df1.copy().rename(columns={
    'DEA_FACILITY_NAME': 'parkingName', 'DEA_FACILITY_ADDRESS': 'fullAddress', 'DEA_STALLS': 'totalCapacity'
})
df_seattle['totalCapacity'] = pd.to_numeric(df_seattle['totalCapacity'], errors='coerce')
df_seattle = df_seattle[['parkingName', 'fullAddress', 'totalCapacity']].dropna(subset=['parkingName', 'totalCapacity'])
df_seattle['city'] = 'Seattle'; df_seattle['state'] = 'WA'
df_seattle['totalCapacity'] = df_seattle['totalCapacity'].fillna(0).astype(int)
df_seattle = df_seattle[df_seattle['totalCapacity'] > 0]

# --- 2. Process File 2 (Sacramento) - Placeholder ---
df_sacramento = pd.DataFrame(columns=['parkingName', 'fullAddress', 'totalCapacity', 'city', 'state'])

# --- 3. Process File 4 (Various US) - Using df2 base ---
df_us_general = df2.copy().rename(columns={
    'Bldg City': 'city', 'Bldg State': 'state', 'Total Parking Spaces': 'totalCapacity'
})
df_us_general['totalCapacity'] = pd.to_numeric(df_us_general['totalCapacity'], errors='coerce').fillna(0)
df_us_general = df_us_general[df_us_general['totalCapacity'] > 0]
df_us_general['totalCapacity'] = df_us_general['totalCapacity'].astype(int)
df_us_general['parkingName'] = df_us_general.apply(
    lambda row: f"{row['city']} {row['state']} Parking Facility {row.name}", axis=1
)
df_us_general['fullAddress'] = df_us_general.apply(
    lambda row: f"Unknown Address in {row['city']}, {row['state']}", axis=1
)
ydf_us_general = df_us_general[['parkingName', 'fullAddress', 'totalCapacity', 'city', 'state']]

# --- 4. Combine and Standardize All Parking Lots ---
all_parking_lots_df = pd.concat([df_seattle, df_sacramento, df_us_general], ignore_index=True)
all_parking_lots_df['parkingLotId'] = [generate_object_id() for _ in range(len(all_parking_lots_df))]
all_parking_lots_df['country'] = 'US'
all_parking_lots_df['availableSlots'] = all_parking_lots_df['totalCapacity']

# --- Collection 3: Slots Collection ---
slots_records = []
parking_lot_ids = all_parking_lots_df['parkingLotId'].tolist()
capacities = all_parking_lots_df['totalCapacity'].tolist()
available_slots = all_parking_lots_df['availableSlots'].tolist() 

for parking_lot_id, capacity, available in zip(parking_lot_ids, capacities, available_slots):
    occupied_count = capacity - available
    statuses = ['OCCUPIED'] * occupied_count + ['AVAILABLE'] * available
    for i in range(capacity):
        floor_char = chr(65 + i // 100)
        slot_number = f"{floor_char}-{i % 100 + 1}"
        slots_records.append({
            '_id': generate_object_id(), 
            'parkingLotId': parking_lot_id, 
            'slotNumber': slot_number,
            'status': statuses[i]
        })
raw_slots_df = pd.DataFrame(slots_records)
slots_df = raw_slots_df[['_id', 'slotNumber', 'status']]

# --- Collection 2: Parking Lots Collection ---
slots_by_lot = raw_slots_df.groupby('parkingLotId')['_id'].apply(list).reset_index(name='slotIds')
parking_lots_df = all_parking_lots_df.copy()
parking_lots_df = parking_lots_df.merge(slots_by_lot, on='parkingLotId', how='left')
parking_lots_df = parking_lots_df.rename(columns={'parkingLotId': '_id'})
parking_lots_df = parking_lots_df[[
    '_id', 'parkingName', 'fullAddress', 'totalCapacity', 'availableSlots', 'slotIds'
]] # Removed city/state

# --- Collection 1: Cities Collection ---
parking_lots_by_city = all_parking_lots_df.groupby(['city', 'state'])['parkingLotId'].apply(list).reset_index(name='parkingLotIds')
cities_df = all_parking_lots_df.drop_duplicates(subset=['city', 'state']).reset_index(drop=True)
cities_df['_id'] = [generate_object_id() for _ in range(len(cities_df))]
cities_df = cities_df[['_id', 'city', 'state', 'country']].rename(columns={'city': 'cityName', 'state': 'stateName'})

cities_df = cities_df.merge(
    parking_lots_by_city, left_on=['cityName', 'stateName'], right_on=['city', 'state'], how='left'
)
cities_df = cities_df[['_id', 'cityName', 'stateName', 'country', 'parkingLotIds']]

# --- Save Base Collections ---
cities_df.to_csv('cities_collection_final.csv', index=False)
parking_lots_df.to_csv('parking_lots_collection_final.csv', index=False)
slots_df.to_csv('slots_collection_final.csv', index=False)

print("Part 1: Base Collections (Cities, Lots, Slots) Generated with MongoDB ObjectIDs.")
