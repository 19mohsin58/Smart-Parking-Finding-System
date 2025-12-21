
import requests
import time
import concurrent.futures
import statistics

# Configuration
BASE_URL = "http://localhost:8080"  # Adjust if running on VM IP
NUM_REQUESTS = 100
CONCURRENT_USERS = 10

class LoadTester:
    def __init__(self, base_url):
        self.base_url = base_url
        self.latencies = []
        self.success_count = 0
        self.fail_count = 0

    def make_request(self, method, endpoint, data=None):
        start_time = time.time()
        try:
            url = f"{self.base_url}{endpoint}"
            if method == 'GET':
                response = requests.get(url)
            elif method == 'POST':
                response = requests.post(url, json=data, headers={'Content-Type': 'application/json'})
            
            # Count 2xx and 4xx (valid bad request) as "successful" protocol interactions for latency testing
            # Adjust stricter if needed
            if response.status_code < 500:
                self.success_count += 1
            else:
                self.fail_count += 1
        except Exception as e:
            self.fail_count += 1
            print(f"Error: {e}")
        finally:
            end_time = time.time()
            self.latencies.append(end_time - start_time)

    def print_stats(self, operation_name):
        if not self.latencies:
            print(f"No successful requests for {operation_name}")
            return

        avg_latency = statistics.mean(self.latencies)
        max_latency = max(self.latencies)
        min_latency = min(self.latencies)
        total_time = sum(self.latencies)
        req_per_sec = len(self.latencies) / total_time if total_time > 0 else 0

        print(f"\n--- Statistics for {operation_name} ---")
        print(f"Total Requests: {len(self.latencies)}")
        print(f"Success: {self.success_count}, Fail: {self.fail_count}")
        print(f"Avg Latency: {avg_latency:.4f} sec")
        print(f"Max Latency: {max_latency:.4f} sec")
        print(f"Min Latency: {min_latency:.4f} sec")
        print(f"Throughput: {req_per_sec:.2f} req/sec")


def test_reads():
    tester = LoadTester(BASE_URL)
    print(f"Starting READ test: {NUM_REQUESTS} requests with {CONCURRENT_USERS} concurrent users...")
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_USERS) as executor:
        # Example Read Endpoint: Get all parking spots (adjust endpoint as needed)
        futures = [executor.submit(tester.make_request, 'GET', '/public/parking-lots/available') for _ in range(NUM_REQUESTS)]
        concurrent.futures.wait(futures)
    
    tester.print_stats("READ (Get Available Slots)")

def test_writes():
    tester = LoadTester(BASE_URL)
    print(f"Starting WRITE test: {NUM_REQUESTS} requests with {CONCURRENT_USERS} concurrent users...")
    
    # Example Write Data
    payload = {
        "userId": "testUser",
        "parkingSlotId": "slot-123",
        "startTime": "2024-01-01T12:00:00",
        "endTime": "2024-01-01T13:00:00"
    }

    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_USERS) as executor:
        # Example Write Endpoint: Book a slot (adjust endpoint as needed)
        futures = [executor.submit(tester.make_request, 'POST', '/public/book', payload) for _ in range(NUM_REQUESTS)]
        concurrent.futures.wait(futures)
    
    tester.print_stats("WRITE (Book Slot)")

if __name__ == "__main__":
    print("Ensure your App is running at localhost:8080 (or port forwarded) before starting.")
    print("Installing requirements: `pip install requests`")
    try:
        test_reads()
        test_writes()
    except KeyboardInterrupt:
        print("Test stopped.")
