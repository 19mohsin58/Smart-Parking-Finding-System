
# Script to Verify MongoDB Read/Write Split (Eventual Consistency)

Write-Host "--- 1. Checking Cluster Status ---" -ForegroundColor Cyan
# Helper to run mongosh command inside container
function Run-MongoCmd {
    param($container, $script)
    docker exec $container mongosh --quiet --eval $script
}

# Find Primary
$primaryNode = ""
$nodes = @("mongo1", "mongo2", "mongo3")
foreach ($node in $nodes) {
    $isPrimary = Run-MongoCmd -container $node -script "rs.isMaster().ismaster"
    if ($isPrimary -eq "true") {
        $primaryNode = $node
        Write-Host "Found PRIMARY: $node" -ForegroundColor Green
    }
}

if ($primaryNode -eq "") {
    Write-Error "No Primary found! Is the cluster running?"
    exit
}

Write-Host "`n--- 2. Enabling Profiling on All Nodes ---" -ForegroundColor Cyan
foreach ($node in $nodes) {
    # Set profiling to level 2 (log everything)
    Run-MongoCmd -container $node -script "db.setProfilingLevel(2)" | Out-Null
    # Clear previous logs
    Run-MongoCmd -container $node -script "db.system.profile.drop()" | Out-Null
    # Re-enable after drop
    Run-MongoCmd -container $node -script "db.createCollection('system.profile', {capped: true, size: 1000000})" | Out-Null
     Run-MongoCmd -container $node -script "db.setProfilingLevel(2)" | Out-Null
    Write-Host "Profiling enabled on $node"
}

Write-Host "`n--- 3. Running Traffic Simulation (performance_test.py) ---" -ForegroundColor Cyan
Write-Host "Sending Writes and Reads..."
python performance_test.py

Write-Host "`n--- 4. Analyzing Operation Distribution ---" -ForegroundColor Cyan

foreach ($node in $nodes) {
    $role = if ($node -eq $primaryNode) { "PRIMARY" } else { "SECONDARY" }
    
    # Count Writes (inserts in 'SPFS_DB')
    $writes = Run-MongoCmd -container $node -script "db.getSiblingDB('SPFS_DB').system.profile.countDocuments({op: 'insert'})"
    
    # Count Reads (queries in 'SPFS_DB', excluding system commands)
    # We look for 'query' or 'command' with 'find'. Filtering out noisy system commands is tricky, 
    # so we filter for our specific collection check if possible or just general 'find' verbs
    $reads = Run-MongoCmd -container $node -script "db.getSiblingDB('SPFS_DB').system.profile.countDocuments({op: 'query'})"
    
    Write-Host "Node: $node ($role)" -ForegroundColor Yellow
    Write-Host "  Writes Attempted (Inserts): $writes"
    Write-Host "  Reads Attempted (Queries):  $reads"
    
    if ($role -eq "PRIMARY") {
        if ([int]$writes -gt 0) { Write-Host "  [OK] Primary is handling writes." -ForegroundColor Green }
        else { Write-Host "  [?] No writes detected on Primary." -ForegroundColor Red }
    } else {
        if ([int]$reads -gt 0) { Write-Host "  [OK] Secondary is handling reads." -ForegroundColor Green }
        # Note: Sometimes reads might still go to primary depending on latency/connection, so this isn't always Red if 0, but usually should have some.
    }
}

Write-Host "`n--- Verification Complete ---" -ForegroundColor Cyan
