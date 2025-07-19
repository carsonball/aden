package org.carball.aden.parser;

import org.carball.aden.model.query.QueryStoreQuery;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Connects to SQL Server Query Store to extract production usage metrics for migration analysis.
 */
public class QueryStoreConnector {
    
    private static final String ALL_QUERIES = """
        SELECT 
            CAST(q.query_id AS VARCHAR) as query_id_str,
            qt.query_sql_text,
            SUM(rs.count_executions) as total_executions,
            AVG(rs.avg_duration / 1000.0) as avg_duration_ms,
            AVG(rs.avg_cpu_time / 1000.0) as avg_cpu_ms,
            AVG(rs.avg_logical_io_reads) as avg_logical_reads
        FROM sys.query_store_query q
        JOIN sys.query_store_query_text qt ON q.query_text_id = qt.query_text_id
        JOIN sys.query_store_plan p ON p.query_id = q.query_id
        JOIN sys.query_store_runtime_stats rs ON rs.plan_id = p.plan_id
        WHERE qt.query_sql_text NOT LIKE '%sys.%'
          AND qt.query_sql_text NOT LIKE '%INFORMATION_SCHEMA%'
          AND qt.query_sql_text NOT LIKE '%query_store%'
        GROUP BY q.query_id, qt.query_sql_text
    """;
    
    // Removed TOP_QUERIES_BY_DURATION - QueryStoreConnector should export all data without filtering
    
    private final String connectionString;
    
    public QueryStoreConnector(String connectionString) {
        this.connectionString = connectionString;
    }
    
    /**
     * Extracts all queries from Query Store without filtering.
     * Analysis and filtering should be done by downstream components.
     */
    public List<QueryStoreQuery> getAllQueries() throws SQLException {
        return executeQueryStoreQuery(ALL_QUERIES);
    }
    
    // Removed getTopQueriesByDuration - filtering should be done by analysis components
    
    /**
     * Tests connection to Query Store and verifies it's enabled.
     */
    public boolean isQueryStoreEnabled() throws SQLException {
        String checkQuery = """
            SELECT actual_state_desc 
            FROM sys.database_query_store_options 
            WHERE actual_state_desc = 'READ_WRITE'
        """;
        
        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement stmt = conn.prepareStatement(checkQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            return rs.next();
        }
    }
    
    /**
     * Gets total number of queries captured in Query Store.
     */
    public int getQueryStoreQueryCount() throws SQLException {
        String countQuery = "SELECT COUNT(*) FROM sys.query_store_query";
        
        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement stmt = conn.prepareStatement(countQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    
    private List<QueryStoreQuery> executeQueryStoreQuery(String query) throws SQLException {
        List<QueryStoreQuery> results = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                QueryStoreQuery queryStoreQuery = new QueryStoreQuery(
                    rs.getString("query_id_str"),
                    rs.getString("query_sql_text"),
                    rs.getLong("total_executions"),
                    rs.getDouble("avg_duration_ms"),
                    rs.getDouble("avg_cpu_ms"),
                    rs.getDouble("avg_logical_reads")
                );
                
                results.add(queryStoreQuery);
            }
        }
        
        return results;
    }
    
    // Removed table analysis methods - this should be done by analysis components, not the connector
    
    /**
     * Creates a connection string for TestEcommerceApp database.
     * Uses Docker SQL Server with SQL Server authentication for cross-platform compatibility.
     */
    public static String createTestAppConnectionString() {
        return "jdbc:sqlserver://localhost:1433;databaseName=TestEcommerceApp;user=sa;password=TestPassword123!;trustServerCertificate=true;encrypt=false;loginTimeout=30;";
    }
    
    /**
     * Main method for standalone testing of QueryStoreConnector.
     */
    public static void main(String[] args) {
        try {
            String connectionString = createTestAppConnectionString();
            System.out.println("Using connection string: " + connectionString);
            QueryStoreConnector connector = new QueryStoreConnector(connectionString);
            
            System.out.println("=== Query Store Connector Test ===");
            
            // Check if Query Store is enabled
            if (!connector.isQueryStoreEnabled()) {
                System.out.println("ERROR: Query Store is not enabled on TestEcommerceApp database");
                return;
            }
            System.out.println("✓ Query Store is enabled");
            
            // Get query count
            int queryCount = connector.getQueryStoreQueryCount();
            System.out.println("✓ Total queries in Query Store: " + queryCount);
            
            if (queryCount == 0) {
                System.out.println("WARNING: No queries found. Run C# TestEcommerceApp simulation first.");
                System.out.println("Make sure to update App.config connection string to: Server=localhost;Database=TestEcommerceApp;User Id=sa;Password=TestPassword123!;TrustServerCertificate=True;");
                return;
            }
            
            // Get all queries from Query Store
            System.out.println("\n=== All Queries from Query Store ===");
            List<QueryStoreQuery> allQueries = connector.getAllQueries();
            
            System.out.printf("Total queries extracted: %d\n", allQueries.size());
            
            // Show first 5 for testing
            for (int i = 0; i < Math.min(5, allQueries.size()); i++) {
                QueryStoreQuery query = allQueries.get(i);
                System.out.printf("\n#%d - Query ID: %s%n", i + 1, query.getQueryId());
                System.out.printf("  Executions: %,d%n", query.getExecutionCount());
                System.out.printf("  Avg Duration: %.2f ms%n", query.getAvgDurationMs());
                // Table analysis removed - connector only exports raw data
                
                String sqlPreview = query.getSqlText().length() > 150 
                    ? query.getSqlText().substring(0, 150) + "..."
                    : query.getSqlText();
                System.out.printf("  SQL: %s%n", sqlPreview.replaceAll("\\s+", " "));
            }
            
            // Analysis removed - QueryStoreConnector is just a data exporter
            System.out.println("\n✓ Query Store data successfully extracted");
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}