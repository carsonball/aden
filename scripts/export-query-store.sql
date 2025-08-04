-- ================================================================
-- Query Store Export Script for Aden Migration Analyzer
-- ================================================================
-- This script exports Query Store metrics to JSON format using SQL Server's
-- built-in JSON functions for secure analysis without database access.
--
-- Prerequisites:
-- - SQL Server 2016+ with Query Store enabled
-- - READ permissions on Query Store system views
--
-- Usage:
-- 1. Execute this script in SSMS
-- 2. Copy the JSON output and save to a .json file
-- 3. Use with Aden: --query-store-file path/to/export.json
-- ================================================================

-- Version check - ensure SQL Server 2016+ for Query Store and JSON support
IF @@VERSION NOT LIKE '%Microsoft SQL Server 2016%' 
   AND @@VERSION NOT LIKE '%Microsoft SQL Server 201[789]%'
   AND @@VERSION NOT LIKE '%Microsoft SQL Server 202[0-9]%'
BEGIN
    PRINT 'ERROR: This script requires SQL Server 2016+ for Query Store and JSON support'
    PRINT 'Current version: ' + @@VERSION
    RETURN
END

-- Configuration
DECLARE @MaxQueries INT = 1000  -- Limit to prevent huge files
DECLARE @MinExecutions INT = 5  -- Only include queries executed at least this many times

-- Metadata collection
DECLARE @DatabaseName NVARCHAR(128) = DB_NAME()
DECLARE @ExportTimestamp NVARCHAR(50) = FORMAT(GETUTCDATE(), 'yyyy-MM-ddTHH:mm:ssZ')
DECLARE @SQLServerVersion NVARCHAR(256) = @@VERSION
DECLARE @QueryStoreEnabled BIT = 0
DECLARE @TotalQueries INT = 0

-- Check Query Store status
SELECT @QueryStoreEnabled = CASE 
    WHEN actual_state_desc IN ('READ_write', 'read_only') THEN 1 
    ELSE 0 
END
FROM sys.database_query_store_options

-- Get total query count
SELECT @TotalQueries = COUNT(*)
FROM sys.query_store_query q
JOIN sys.query_store_query_text qt ON q.query_text_id = qt.query_text_id
WHERE qt.query_sql_text NOT LIKE '%sys.%'
  AND qt.query_sql_text NOT LIKE '%INFORMATION_SCHEMA%'
  AND qt.query_sql_text NOT LIKE '%query_store%'

-- Validate Query Store is available
IF @QueryStoreEnabled = 0
BEGIN
    PRINT 'ERROR: Query Store is not enabled on database ' + @DatabaseName
    PRINT 'Enable Query Store with: ALTER DATABASE [' + @DatabaseName + '] SET QUERY_STORE = ON'
    RETURN
END

PRINT 'Exporting Query Store data from database: ' + @DatabaseName
PRINT 'Query Store enabled: Yes'
PRINT 'Total queries available: ' + CAST(@TotalQueries AS NVARCHAR(10))
PRINT ''
PRINT '=== JSON Export (Copy this content to a .json file) ==='

-- Build JSON export using SQL Server JSON functions
DECLARE @JsonOutput NVARCHAR(MAX)

SET @JsonOutput = (
    SELECT 
        (
            SELECT 
                @DatabaseName as database_name,
                @ExportTimestamp as export_timestamp,
                @SQLServerVersion as sql_server_version,
                @QueryStoreEnabled as query_store_enabled,
                @TotalQueries as total_queries
            FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
        ) as export_metadata,
        (
            SELECT TOP (@MaxQueries)
                CAST(q.query_id AS NVARCHAR(50)) as query_id,
                -- Clean and sanitize SQL text
                LEFT(LTRIM(RTRIM(
                    REPLACE(
                        REPLACE(
                            REPLACE(qt.query_sql_text, CHAR(13), ' '), 
                            CHAR(10), ' '
                        ), 
                        CHAR(9), ' '
                    )
                )), 2000) as sql_text,
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
              AND qt.query_sql_text NOT LIKE '%sp_help%'
              AND qt.query_sql_text NOT LIKE '%EXEC sp_%'
              AND LEN(qt.query_sql_text) > 20
              AND rs.count_executions >= @MinExecutions
            GROUP BY q.query_id, qt.query_sql_text
            ORDER BY SUM(rs.count_executions) DESC
            FOR JSON PATH
        ) as queries
    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
)

-- Output the JSON
PRINT @JsonOutput

PRINT '=== End of JSON Export ==='
PRINT ''
PRINT 'Export completed successfully!'
PRINT 'Next steps:'
PRINT '1. Save the JSON output above to a file (e.g., query-store-export.json)'
PRINT '2. Run Aden analyzer with: --query-store-file query-store-export.json'
PRINT '3. This eliminates the need to provide database connection strings'

-- Export summary
SELECT 
    'Export Summary' as Status,
    @DatabaseName as DatabaseName,
    @ExportTimestamp as ExportTimestamp,
    CASE WHEN @QueryStoreEnabled = 1 THEN 'Enabled' ELSE 'Disabled' END as QueryStoreStatus,
    @TotalQueries as TotalQueriesAvailable,
    @MaxQueries as MaxQueriesExported,
    @MinExecutions as MinExecutionThreshold