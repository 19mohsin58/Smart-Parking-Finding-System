# CAP Theorem Analysis & Performance Considerations

## 1. CAP Theorem Overview
 The CAP theorem states that a distributed data store can only provide **two** of the following three guarantees simultaneously:
*   **Consistency (C):** Every read receives the most recent write or an error.
*   **Availability (A):** Every request receives a (non-error) response, without the guarantee that it contains the most recent write.
*   **Partition Tolerance (P):** The system continues to operate despite an arbitrary number of messages being dropped or delayed by the network between nodes.

## 2. SPFS Architecture Analysis
Your current architecture generally falls into the **CP (Consistency/Partition Tolerance)** or **AP (Availability/Partition Tolerance)** spectrum depending on the specific operation, but with your specific configuration (**Replica Set + Eventual Consistency**), it behaves as follows:

### MongoDB (Database Layer)
*   **Partition Tolerance (P):** MongoDB is built to be partition-tolerant. If the network between nodes fails, the system tries to elect a new Primary.
*   **Write Operations (CP):** Writes **must** go to the Primary node. If the Primary is unreachable (Partition), writes will fail until a new Primary is elected. This prioritizes **Consistency** (preventing split-brain data conflicts) over Availability.
*   **Read Operations (AP-like behavior):** You configured `readPreference=secondaryPreferred`.
    *   **Availability (A):** The application will read from Secondary nodes if the Primary is busy or down. This increases Availability.
    *   **Consistency (C):** **SACRIFICED**. Because replication from Primary to Secondary takes time (milliseconds to seconds), a user might read "stale" data immediately after a write. This is **Eventual Consistency**.

### Redis (Caching Layer)
*   **Single Node:** In the current specific lab setup (1 node), CAP doesn't strictly apply as there is no partition to tolerate. Ideally, in a cluster, Redis is often configured as AP or CP depending on the Sentinel/Cluster mode.

## 3. Evaluating Read vs. Write Performance

To satisfy the requirement "statistics and performance tests when evaluating read and write operations", we must measure:
1.  **Throughput (Requests per Second):** How many operations can the cluster handle?
2.  **Latency (Response Time):** How long does one request take?

### Expected Behaviors
*   **Reads (High Performance):** distributed across 3 nodes (1 Primary + 2 Secondaries). Scaling the K8s app replicas to 3 allows parallel processing.
*   **Writes (Bottleneck):** All writes must go to the single Primary MongoDB node. Scaling the app replicas doesn't strictly increase write throughput at the DB layer, as the DB Primary is the limiter.

## 4. Testing Strategy
We will use a Python script (`performance_test.py`) to simulate this:
*   **Write Test:** Simulate multiple users Booking slots simultaneously.
*   **Read Test:** Simulate multiple users searching for slots simultaneously.
*   **Metric:** We will measure the "Time to Complete" and "Success Rate".
