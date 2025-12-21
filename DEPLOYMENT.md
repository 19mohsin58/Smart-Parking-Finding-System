# Deployment Strategy: Local vs Virtual Lab

This guide explains how to deploy your system in two environments: your **Local Cluster** for development and the **Virtual Lab** for the final demo.

## 1. What is the Difference?

### A. Local Cluster (Your Computer)
*   **What it is:** Using **Docker Desktop** (with Kubernetes enabled) or **Minikube** on your own laptop.
*   **Purpose:** Development, testing, and debugging.
*   **Alignment:** 
    *   **Simpler option:** Use `docker-compose up` to run everything as containers on a single network.
    *   **Cluster option:** Use `kubectl apply -f k8s/` to simulate the production environment right on your PC.

### B. Virtual Lab (University Infrastructure)
*   **What it is:** A set of Virtual Machines (VMs) or a Kubernetes Cluster provided by your instructions.
*   **Purpose:** Final deployment, proving "Horizontal Scaling" and "High Availability" in a real networked environment.
*   **Alignment:** You use the **EXACT SAME** `k8s/` files. Kubernetes allows you to take the manifests you tested locally and apply them to the remote lab without changing code.

---

## 2. Deployment Instructions

### Option A: Local Run (Docker Compose)
Best for quick testing.
1.  **Build & Run:**
    ```powershell
    docker-compose up --build
    ```
2.  **Access:**
    *   App: `http://localhost:8080`
    *   Mongo: `localhost:27017`

### Option B: The "Cluster" Deployment (Kubernetes)
Use this for both **Minikube (Local)** and the **Virtual Lab**.

#### Prerequisites for Virtual Lab
1.  **VPN/Access:** Ensure you are connected to the University network.
2.  **Config:** Your professor will give you a `kubeconfig` file.
    *   Command: `set KUBECONFIG=path\to\professor_file.yaml` (PowerShell)
    *   Test connection: `kubectl get nodes` (You should see the Uni's nodes).

#### Deployment Steps
*Run these commands in your PowerShell terminal inside the project folder.*

**Step 1: Deploy Database Layer (Ordered)**
```powershell
# 1. Start Redis
kubectl apply -f k8s/database-layer.yaml

# 2. Start MongoDB Replica Set (3 Nodes)
kubectl apply -f k8s/mongo-statefulset.yaml

# 3. Wait for database pods to be "Running"
kubectl get pods
# output should show: mongo-0, mongo-1, mongo-2

# 4. Connect the 3 nodes together (Initialize Replica Set)
kubectl apply -f k8s/mongo-init-configmap.yaml
kubectl apply -f k8s/mongo-init-job.yaml
```

**Step 2: Deploy Your Application**
*Note: In the Virtual Lab, you might need to push your docker image to a registry (like DockerHub) first, or build it directly on the Lab machine. If you can't push images, ask how to "load" images into the Lab.*
```powershell
kubectl apply -f k8s/app-deployment.yaml
kubectl apply -f k8s/app-service.yaml
```

**Step 3: Verification**
1.  **Check Status:** `kubectl get pods` (All should be `Running`).
2.  **Check Logs:** `kubectl logs -l app=spfs-app` (Should say "Connected to MongoDB").

## 3. How to Check Horizontal Scaling (For Professor)
Once running in the cluster (Local or Virtual):

1.  **Initial Count:**
    ```powershell
    kubectl get pods -l app=spfs-app
    # Shows 1 pod
    ```

2.  **Scale Up:**
    ```powershell
    kubectl scale deployment spfs-app --replicas=3
    ```

3.  **Prove It:**
    ```powershell
    kubectl get pods -l app=spfs-app -w
    # Watch as new pods appear: spfs-app-xxxxx (ContainerCreating) -> (Running)
    ```
