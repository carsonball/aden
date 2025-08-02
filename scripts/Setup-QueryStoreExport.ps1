# ================================================================
# PowerShell Script to Set Up Automated Query Store Export
# ================================================================
# This script helps organizations set up automated, scheduled exports
# of Query Store data for use with the Aden Migration Analyzer.
#
# Prerequisites:
# - SQL Server 2016+ with Query Store enabled
# - PowerShell with SqlServer module
# - Windows Task Scheduler (for automation)
# - Appropriate SQL Server permissions
#
# Usage:
# .\Setup-QueryStoreExport.ps1 -ServerInstance "localhost" -Database "TestApp" -OutputPath "C:\QueryStoreExports"
# ================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$ServerInstance,
    
    [Parameter(Mandatory=$true)]
    [string]$Database,
    
    [Parameter(Mandatory=$true)]
    [string]$OutputPath,
    
    [string]$SqlUsername = $null,
    [string]$SqlPassword = $null,
    [bool]$UseWindowsAuth = $true,
    [bool]$SetupScheduledTask = $true,
    [string]$ScheduleTime = "02:00",  # Default to 2 AM daily
    [int]$RetentionDays = 30
)

# Check if SqlServer module is available
if (-not (Get-Module -ListAvailable -Name SqlServer)) {
    Write-Error "SqlServer PowerShell module is required. Install with: Install-Module SqlServer"
    exit 1
}

Import-Module SqlServer

Write-Host "🔧 Setting up Query Store export automation" -ForegroundColor Green
Write-Host "   Server: $ServerInstance"
Write-Host "   Database: $Database"
Write-Host "   Output Path: $OutputPath"
Write-Host ""

# Create output directory if it doesn't exist
if (-not (Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
    Write-Host "✓ Created output directory: $OutputPath" -ForegroundColor Green
}

# Build connection string
$connectionString = if ($UseWindowsAuth) {
    "Server=$ServerInstance;Database=$Database;Integrated Security=true;"
} else {
    if (-not $SqlUsername -or -not $SqlPassword) {
        Write-Error "SQL Username and Password required when not using Windows Authentication"
        exit 1
    }
    "Server=$ServerInstance;Database=$Database;User Id=$SqlUsername;Password=$SqlPassword;"
}

# Test connection and Query Store status
Write-Host "🔍 Testing connection and Query Store status..." -ForegroundColor Yellow

try {
    $queryStoreStatus = Invoke-Sqlcmd -ConnectionString $connectionString -Query @"
        SELECT 
            DB_NAME() as DatabaseName,
            actual_state_desc as QueryStoreStatus,
            readonly_reason_desc as ReadOnlyReason
        FROM sys.database_query_store_options
"@

    if ($queryStoreStatus.QueryStoreStatus -notin @('READ_WRITE', 'READ_ONLY')) {
        Write-Error "Query Store is not enabled on database '$Database'. Status: $($queryStoreStatus.QueryStoreStatus)"
        Write-Host "Enable with: ALTER DATABASE [$Database] SET QUERY_STORE = ON" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host "✓ Query Store is enabled and accessible" -ForegroundColor Green
    Write-Host "   Status: $($queryStoreStatus.QueryStoreStatus)" -ForegroundColor Gray
    
} catch {
    Write-Error "Failed to connect to SQL Server or check Query Store status: $($_.Exception.Message)"
    exit 1
}

# Create the export script
$exportScriptPath = Join-Path $OutputPath "Export-QueryStore.ps1"
$exportScript = @"
# Auto-generated Query Store export script
# Generated on: $(Get-Date)
# Target Database: $Database on $ServerInstance

param(
    [string]`$OutputFile = `$null
)

Import-Module SqlServer -ErrorAction Stop

`$connectionString = "$connectionString"
`$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
`$defaultOutputFile = "$(Join-Path $OutputPath "query-store-export-`$timestamp.json")"
`$outputFile = if (`$OutputFile) { `$OutputFile } else { `$defaultOutputFile }

Write-Host "📈 Exporting Query Store data from $Database..." -ForegroundColor Green
Write-Host "   Output: `$outputFile"

try {
    # Execute the T-SQL export script
    `$exportSql = Get-Content "$(Join-Path (Split-Path $PSScriptRoot) "export-query-store.sql")" -Raw
    
    # Capture the JSON output
    `$result = Invoke-Sqlcmd -ConnectionString `$connectionString -Query `$exportSql -Verbose:`$false
    
    # The JSON will be in the result messages - we need to extract it
    # For now, we'll use a simpler approach with direct query
    `$jsonQuery = @"
SELECT 
    '$Database' as database_name,
    FORMAT(GETUTCDATE(), 'yyyy-MM-ddTHH:mm:ssZ') as export_timestamp,
    @@VERSION as sql_server_version,
    CASE WHEN EXISTS(SELECT 1 FROM sys.database_query_store_options WHERE actual_state_desc IN ('READ_write', 'read_only')) THEN 1 ELSE 0 END as query_store_enabled,
    (SELECT COUNT(*) FROM sys.query_store_query) as total_queries,
    (
        SELECT TOP 1000
            CAST(q.query_id AS NVARCHAR(50)) as query_id,
            LEFT(LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(qt.query_sql_text, CHAR(13), ' '), CHAR(10), ' '), CHAR(9), ' '))), 2000) as sql_text,
            SUM(rs.count_executions) as execution_count,
            ROUND(AVG(rs.avg_duration / 1000.0), 3) as avg_duration_ms,
            ROUND(AVG(rs.avg_cpu_time / 1000.0), 3) as avg_cpu_ms,
            ROUND(AVG(rs.avg_logical_io_reads), 2) as avg_logical_reads
        FROM sys.query_store_query q
        JOIN sys.query_store_query_text qt ON q.query_text_id = qt.query_text_id
        JOIN sys.query_store_plan p ON p.query_id = q.query_id
        JOIN sys.query_store_runtime_stats rs ON rs.plan_id = p.plan_id
        WHERE qt.query_sql_text NOT LIKE '%sys.%'
          AND qt.query_sql_text NOT LIKE '%INFORMATION_SCHEMA%'
          AND qt.query_sql_text NOT LIKE '%query_store%'
          AND LEN(qt.query_sql_text) > 20
          AND rs.count_executions >= 5
        GROUP BY q.query_id, qt.query_sql_text
        ORDER BY SUM(rs.count_executions) DESC
        FOR JSON PATH
    ) as queries
FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
"@
    
    `$jsonData = Invoke-Sqlcmd -ConnectionString `$connectionString -Query `$jsonQuery -Verbose:`$false
    `$jsonData.Column1 | Out-File -FilePath `$outputFile -Encoding UTF8
    
    Write-Host "✓ Export completed successfully" -ForegroundColor Green
    Write-Host "   File: `$outputFile"
    Write-Host "   Size: $((Get-Item `$outputFile).Length) bytes"
    
    # Clean up old files if retention policy is set
    if ($RetentionDays -gt 0) {
        `$cutoffDate = (Get-Date).AddDays(-$RetentionDays)
        Get-ChildItem -Path "$OutputPath" -Filter "query-store-export-*.json" | 
            Where-Object { `$_.CreationTime -lt `$cutoffDate } | 
            ForEach-Object {
                Remove-Item `$_.FullName -Force
                Write-Host "   Cleaned up old file: `$(`$_.Name)" -ForegroundColor Gray
            }
    }
    
} catch {
    Write-Error "Export failed: `$(`$_.Exception.Message)"
    exit 1
}
"@

$exportScript | Out-File -FilePath $exportScriptPath -Encoding UTF8
Write-Host "✓ Created export script: $exportScriptPath" -ForegroundColor Green

# Set up scheduled task if requested
if ($SetupScheduledTask) {
    Write-Host "📅 Setting up scheduled task..." -ForegroundColor Yellow
    
    $taskName = "QueryStore-Export-$Database"
    $taskDescription = "Automated Query Store export for Aden Migration Analyzer"
    
    # Remove existing task if it exists
    if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
        Write-Host "   Removed existing scheduled task" -ForegroundColor Gray
    }
    
    # Create new scheduled task
    $action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File `"$exportScriptPath`""
    $trigger = New-ScheduledTaskTrigger -Daily -At $ScheduleTime
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive
    
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description $taskDescription | Out-Null
    
    Write-Host "✓ Scheduled task created: $taskName" -ForegroundColor Green
    Write-Host "   Schedule: Daily at $ScheduleTime" -ForegroundColor Gray
    Write-Host "   Command: $exportScriptPath" -ForegroundColor Gray
}

# Test the export script
Write-Host "🧪 Testing export script..." -ForegroundColor Yellow
$testOutputFile = Join-Path $OutputPath "test-export.json"

try {
    & $exportScriptPath -OutputFile $testOutputFile
    
    if (Test-Path $testOutputFile) {
        $testFileSize = (Get-Item $testOutputFile).Length
        Write-Host "✓ Test export successful" -ForegroundColor Green
        Write-Host "   Test file: $testOutputFile ($testFileSize bytes)"
        
        # Validate JSON structure
        try {
            $testJson = Get-Content $testOutputFile -Raw | ConvertFrom-Json
            if ($testJson.database_name -and $testJson.queries) {
                Write-Host "✓ JSON structure is valid" -ForegroundColor Green
                Write-Host "   Database: $($testJson.database_name)"
                Write-Host "   Query count: $($testJson.queries.Count)"
            } else {
                Write-Warning "JSON structure may be incomplete"
            }
        } catch {
            Write-Warning "Could not validate JSON structure: $($_.Exception.Message)"
        }
        
        # Clean up test file
        Remove-Item $testOutputFile -Force
    } else {
        Write-Error "Test export failed - no output file created"
    }
} catch {
    Write-Error "Test export failed: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "🎉 Setup completed successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. The scheduled task will run daily at $ScheduleTime"
Write-Host "2. Export files will be saved to: $OutputPath"
Write-Host "3. Use exported files with Aden analyzer: --query-store-file <path-to-export.json>"
Write-Host "4. Old files will be cleaned up after $RetentionDays days"
Write-Host ""
Write-Host "Manual export: & `"$exportScriptPath`"" -ForegroundColor Gray