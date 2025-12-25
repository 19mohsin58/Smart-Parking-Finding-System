
import requests
import time
import concurrent.futures
import statistics
import random
import uuid

# Configuration
BASE_URL = "http://localhost:8080"
API_PUBLIC = f"{BASE_URL}/api/public"

# Test Settings
NUM_READ_REQUESTS = 100
NUM_WRITE_REQUESTS = 100 # Keep small to avoid filling up DB too fast during dev
CONCURRENT_USERS = 50

class LoadTester:
    def __init__(self):
        self.latencies = []
        self.status_codes = {}
        self.failed_requests = []

    def record_request(self, method, endpoint, payload=None):
        start_time = time.time()
        status_code = 0
        try:
            url = f"{BASE_URL}{endpoint}"
            headers = {'Content-Type': 'application/json'}
            
            if method == 'GET':
                response = requests.get(url, headers=headers, timeout=5)
            elif method == 'POST':
                response = requests.post(url, json=payload, headers=headers, timeout=5)
            
            status_code = response.status_code
            
            # For debugging failed standard requests
            if status_code >= 400:
                self.failed_requests.append(f"{method} {endpoint} -> {status_code}: {response.text}")

        except Exception as e:
            status_code = 999 # Internal client error
            self.failed_requests.append(f"{method} {endpoint} -> Exception: {str(e)}")
        finally:
            end_time = time.time()
            self.latencies.append(end_time - start_time)
            self.status_codes[status_code] = self.status_codes.get(status_code, 0) + 1
            return status_code

    def print_stats(self, test_name):
        if not self.latencies:
            print(f"\n[!] No data for {test_name}")
            return

        avg_latency = statistics.mean(self.latencies)
        max_latency = max(self.latencies)
        min_latency = min(self.latencies)
        total_requests = len(self.latencies)
        success_count = sum(count for code, count in self.status_codes.items() if 200 <= code < 300)
        
        print(f"\n{'='*20} {test_name} {'='*20}")
        print(f"Total Requests: {total_requests}")
        print(f"Success (2xx):  {success_count}")
        print(f"Status Codes:   {self.status_codes}")
        print(f"Avg Latency:    {avg_latency:.4f} sec")
        print(f"Max Latency:    {max_latency:.4f} sec")
        print(f"Min Latency:    {min_latency:.4f} sec")
        
        if self.failed_requests:
            print("\n--- Sample Failed Requests (First 3) ---")
            for fail in self.failed_requests[:3]:
                print(fail)
        print("="*60)

def setup_test_data():
    """Dynamically fetches a valid City and Parking Lot ID to use for testing."""
    print("[*] Setting up test data (Manual Override)...")
    
    # User provided hardcoded values
    target_city = "WINDSOR LOCKS"
    target_lot_id = "693ea975fec7a5540d209efd" # DIAMOND PARKING A842
    
    print(f"[*] Using Hardcoded City: {target_city}")
    print(f"[*] Using Hardcoded Lot ID: {target_lot_id}")
    
    return target_city, target_lot_id

def run_read_test(target_city):
    tester = LoadTester()
    print(f"\n[*] Starting READ Test (Concurrency: {CONCURRENT_USERS})...")
    
    endpoint = f"/api/public/cities/{target_city}/parking-lots"
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_USERS) as executor:
        futures = [executor.submit(tester.record_request, 'GET', endpoint) for _ in range(NUM_READ_REQUESTS)]
        concurrent.futures.wait(futures)
        
    tester.print_stats("READ TEST: Get Available Slots")

def run_write_test_concurrent(target_lot_id):
    """
    Simulates multiple users trying to book at the same time.
    This tests the 'Consistency' aspect - we expect successful bookings only up to capacity.
    """
    tester = LoadTester()
    print(f"\n[*] Starting WRITE/CONSISTENCY Test (Concurrency: {CONCURRENT_USERS})...")
    
    # Pre-generate unique user IDs to simulate different users
    tasks = []
    
    for _ in range(NUM_WRITE_REQUESTS):
        user_id = f"perf_user_{uuid.uuid4().hex[:8]}"
        payload = {
            "userId": user_id,
            "parkingLotId": target_lot_id,
            "vehicleNumber": f"VEH-{random.randint(1000, 9999)}",
            "hours": 1
        }
        tasks.append(payload)

    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_USERS) as executor:
        futures = [executor.submit(tester.record_request, 'POST', "/api/public/book", payload) for payload in tasks]
        concurrent.futures.wait(futures)

    tester.print_stats("WRITE TEST: Concurrent Bookings")

if __name__ == "__main__":
    city, lot_id = setup_test_data()
    
    if city and lot_id:
        run_read_test(city)
        run_write_test_concurrent(lot_id)
    else:
        print("[!] Aborting tests due to setup failure.")
