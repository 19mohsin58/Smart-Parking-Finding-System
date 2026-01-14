
# Script to Verify MongoDB Read/Write Split - Hybrid (Local + VMs)

Write-Host "--- 1. Checking Cluster Status ---" -ForegroundColor Cyan

# ================= CONFIGURATION =================
# Set this to "LOCAL" or "VM"
$DeploymentMode = "VM"

$baseUrl = "http://localhost:8080" # App URL (Keep localhost if running app locally)

if ($DeploymentMode -eq "LOCAL") {
    Write-Host "[*] Mode: LOCALHOST (Ports 27017, 27018, 27019)" -ForegroundColor Yellow
    $nodes = @(
        @{ Name="Local-1"; Host="localhost"; Port=27017 },
        @{ Name="Local-2"; Host="localhost"; Port=27018 },
        @{ Name="Local-3"; Host="localhost"; Port=27019 }
    )
} 
elseif ($DeploymentMode -eq "VM") {
    Write-Host "[*] Mode: VIRTUAL MACHINES (10.1.1.93, .94, .95)" -ForegroundColor Yellow
    $nodes = @(
        @{ Name="VM-1"; Host="10.1.1.93"; Port=27017 },
        @{ Name="VM-2"; Host="10.1.1.94"; Port=27017 },
        @{ Name="VM-3"; Host="10.1.1.95"; Port=27017 }
    )
} 
else {
    Write-Error "Invalid DeploymentMode. Use 'LOCAL' or 'VM'."
    exit
}
# =================================================

$primaryNode = $null

# Helper to run mongosh
function Run-MongoCmd {
    param($targetHost, $port, $script)
    # Output raw text from mongosh connecting to specific host:port
    
    $args = @("--host", $targetHost, "--port", $port, "--quiet", "--eval", $script)
    
    if ($MongoUsername -ne "" -and $MongoPassword -ne "") {
        # Assuming admin auth source, adjust if needed
        $args += "--username"
        $args += $MongoUsername
        $args += "--password"
        $args += $MongoPassword
        $args += "--authenticationDatabase"
        $args += "admin" 
    }

    # Debug: Print what we are doing
    # Write-Host "DEBUG: Executing mongosh $args" -ForegroundColor DarkGray
    
    try {
        $raw = mongosh @args 2>&1
    } catch {
        return "ERROR: mongosh execution failed: $_"
    }

    # Debug: Print raw output if it doesn't look right
    if ($raw -notmatch "true" -and $raw -notmatch "false" -and $raw -notmatch "Long" -and $raw -notmatch "^\d+$") {
         Write-Host "DEBUG: mongosh returned: $raw" -ForegroundColor Magenta
    }
    
    # 1. Handle Booleans (for isMaster check)
    if ($raw -match "true") { return "true" }
    if ($raw -match "false") { return "false" }

    # 2. Handle MongoDB Longs (e.g. Long('123'))
    if ($raw -match "Long\('(\d+)'\)") {
        return $matches[1]
    } 
    
    # 3. Handle Plain Numbers
    if ($raw -match "^(\d+)$") {
        return $matches[1]
    }
    
    return $raw
}

# 1. Identify Primary
foreach ($node in $nodes) {
    # Perform a TCP check first, but only as a warning
    try {
        $test = Test-NetConnection -ComputerName $node.Host -Port $node.Port -InformationLevel Quiet -ErrorAction SilentlyContinue
        if ($test.TcpTestSucceeded) {
            Write-Host "OK: $($node.Host)`:$($node.Port) is reachable." -ForegroundColor Green
        } else {
            Write-Host "WARNING: TCP Check failed for $($node.Host)`:$($node.Port). Trying connection anyway..." -ForegroundColor Yellow
        }
    } catch {
        Write-Warning "Could not perform TCP check for $($node.Host):$($node.Port). Trying connection anyway..."
    }

    # Now attempt to connect with mongosh
    try {
        $isPrimary = Run-MongoCmd -targetHost $node.Host -port $node.Port -script "rs.isMaster().ismaster"
        if ($isPrimary -eq "true") {
            $primaryNode = $node
            Write-Host "Found PRIMARY at $($node.Host):$($node.Port)" -ForegroundColor Green
        } else {
             # Print the actual output/error if it failed to find a primary
             Write-Host "WARNING: Could not identify Primary at $($node.Host):$($node.Port)." -ForegroundColor Yellow
             Write-Host "       Output: $isPrimary" -ForegroundColor Magenta
        }
    } catch {
        Write-Warning "Could not connect to $($node.Host):$($node.Port) with mongosh. Error: $_"
    }
}

if ($null -eq $primaryNode) {
    Write-Error "No Primary found! Is the cluster running?"
    exit
}

# 2. Capture Baseline Counters
Write-Host "`n--- 2. Capturing Baseline Operation Counters ---" -ForegroundColor Cyan
$baselines = @{}
foreach ($node in $nodes) {
    $role = if ($node.Host -eq $primaryNode.Host -and $node.Port -eq $primaryNode.Port) { "PRIMARY" } else { "SECONDARY" }
    
    # Get total inserts and queries since startup
    $inserts = [long](Run-MongoCmd -host $node.Host -port $node.Port -script "db.serverStatus().opcounters.insert")
    $queries = [long](Run-MongoCmd -host $node.Host -port $node.Port -script "db.serverStatus().opcounters.query")
    
    # Use a unique key for the dictionary (Host:Port)
    $key = "$($node.Host):$($node.Port)"
    $baselines[$key] = @{ 
        Inserts = $inserts; 
        Queries = $queries 
    }
    Write-Host "$key ($role) -> Inserts: $inserts | Queries: $queries" -ForegroundColor Gray
}

Write-Host "`n--- 3. Generating Traffic (PowerShell) ---" -ForegroundColor Cyan

# A. Generate READ Traffic (GET Requests) -> Should go to SECONDARY
Write-Host "[*] Sending 50 READ Requests (GET /api/public/cities)..." -ForegroundColor Yellow
for ($i=1; $i -le 50; $i++) {
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/public/cities" -Method Get -ErrorAction SilentlyContinue | Out-Null
    } catch {}
}

# B. Generate WRITE Traffic (POST Requests) -> Should go to PRIMARY
Write-Host "[*] Sending 10 WRITE Requests (POST /api/public/register)..." -ForegroundColor Yellow
for ($i=1; $i -le 10; $i++) {
    $randomEmail = "perf_" + (Get-Random) + "@hybrid.com"
    $body = @{
        fullName = "Hybrid User"
        email = $randomEmail
        password = "password123"
        state = "TX"
        cityName = "Texas City"
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "$baseUrl/api/public/register" -Method Post -Body $body -ContentType "application/json" -ErrorAction SilentlyContinue | Out-Null
    } catch {}
}

# Wait for counters to update
Start-Sleep -Seconds 3

Write-Host "`n--- 4. Analyzing Traffic Distribution ---" -ForegroundColor Cyan

foreach ($node in $nodes) {
    $role = if ($node.Host -eq $primaryNode.Host -and $node.Port -eq $primaryNode.Port) { "PRIMARY" } else { "SECONDARY" }
    
    # Get New Counters
    $currentInserts = [long](Run-MongoCmd -host $node.Host -port $node.Port -script "db.serverStatus().opcounters.insert")
    $currentQueries = [long](Run-MongoCmd -host $node.Host -port $node.Port -script "db.serverStatus().opcounters.query")
    
    $key = "$($node.Host):$($node.Port)"
    $deltaInserts = $currentInserts - $baselines[$key].Inserts
    $deltaQueries = $currentQueries - $baselines[$key].Queries
    
    Write-Host "$key ($role)" -ForegroundColor Yellow
    Write-Host "  New Writes: $deltaInserts"
    Write-Host "  New Reads : $deltaQueries"
    
    if ($role -eq "PRIMARY") {
        if ($deltaInserts -gt 0) { 
            Write-Host "  [PASS] Primary handled WRITE traffic (Expected)." -ForegroundColor Green 
        } else {
            Write-Host "  [WARN] Primary saw no new writes." -ForegroundColor Gray
        }
    } else {
        if ($deltaQueries -gt 0) {
            Write-Host "  [PASS] Secondary handled READ traffic!" -ForegroundColor Green
        } else {
             Write-Host "  [INFO] No new reads on this Secondary." -ForegroundColor Gray
        }
    }
}

Write-Host "`n--- Verification Complete ---" -ForegroundColor Cyan
