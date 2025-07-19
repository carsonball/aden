package org.carball.aden.parser;

import org.carball.aden.model.query.QueryStoreQuery;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects to SQL Server Query Store to extract production usage metrics for migration analysis.
 */
public class QueryStoreConnector {
    
    private static final String TOP_QUERIES_BY_EXECUTION = """
        SELECT TOP 20 
            CAST(q.query_id AS VARCHAR) as query_id_str,
            qt.query_sql_text,
            SUM(rs.count_executions) as total_executions,
            AVG(rs.avg_duration / 1000.0) as avg_duration_ms,
            AVG(rs.avg_cpu_time / 1000.0) as avg_cpu_ms,
            AVG(rs.avg_logical_io_reads) as avg_logical_reads
        FROM sys.query_store_query q
        JOIN sys.query_store_query_text qt ON q.query_text_id = qt.query_text_id  
        JOIN sys.query_store_runtime_stats rs ON q.query_id = rs.query_id
        WHERE qt.query_sql_text NOT LIKE '%sys.%'
          AND qt.query_sql_text NOT LIKE '%INFORMATION_SCHEMA%'
          AND qt.query_sql_text NOT LIKE '%query_store%'
        GROUP BY q.query_id, qt.query_sql_text
        HAVING SUM(rs.count_executions) > 5
        ORDER BY total_executions DESC
    """;
    
    private static final String TOP_QUERIES_BY_DURATION = """
        SELECT TOP 20 
            CAST(q.query_id AS VARCHAR) as query_id_str,
            qt.query_sql_text,
            SUM(rs.count_executions) as total_executions,
            AVG(rs.avg_duration / 1000.0) as avg_duration_ms,
            AVG(rs.avg_cpu_time / 1000.0) as avg_cpu_ms,
            AVG(rs.avg_logical_io_reads) as avg_logical_reads
        FROM sys.query_store_query q
        JOIN sys.query_store_query_text qt ON q.query_text_id = qt.query_text_id  
        JOIN sys.query_store_runtime_stats rs ON q.query_id = rs.query_id
        WHERE qt.query_sql_text NOT LIKE '%sys.%'
          AND qt.query_sql_text NOT LIKE '%INFORMATION_SCHEMA%'
          AND qt.query_sql_text NOT LIKE '%query_store%'
        GROUP BY q.query_id, qt.query_sql_text
        HAVING SUM(rs.count_executions) > 5
        ORDER BY avg_duration_ms DESC
    """;
    
    private final String connectionString;
    
    public QueryStoreConnector(String connectionString) {
        this.connectionString = connectionString;
    }
    
    /**
     * Extracts top queries by execution count from Query Store.
     */
    public List<QueryStoreQuery> getTopQueriesByExecution() throws SQLException {
        return executeQueryStoreQuery(TOP_QUERIES_BY_EXECUTION);
    }
    
    /**
     * Extracts top queries by average duration from Query Store.
     */
    public List<QueryStoreQuery> getTopQueriesByDuration() throws SQLException {
        return executeQueryStoreQuery(TOP_QUERIES_BY_DURATION);
    }
    
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
                
                // Parse SQL to extract table relationships
                enhanceWithTableAnalysis(queryStoreQuery);
                
                results.add(queryStoreQuery);
            }
        }
        
        return results;
    }
    
    /**
     * Analyzes SQL text to extract table names and join patterns.
     */
    private void enhanceWithTableAnalysis(QueryStoreQuery query) {
        String sql = query.getSqlText().toUpperCase();
        
        // Extract table names from FROM and JOIN clauses
        List<String> tables = extractTableNames(sql);
        query.setTablesAccessed(tables);
        
        // Determine join pattern
        String joinPattern = determineJoinPattern(sql);
        query.setJoinPattern(joinPattern);
    }
    
    private List<String> extractTableNames(String sql) {
        List<String> tables = new ArrayList<>();
        
        // Pattern to match table names after FROM and JOIN keywords
        // Handles aliases: FROM Customers c, JOIN CustomerProfiles p
        Pattern tablePattern = Pattern.compile(
            "(?:FROM|JOIN)\\s+(?:\\[)?([A-Za-z_][A-Za-z0-9_]*)(?:\\])?(?:\\s+(?:AS\\s+)?[A-Za-z_][A-Za-z0-9_]*)?",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = tablePattern.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            // Filter out common system/temp table patterns
            if (!tableName.startsWith("sys") && 
                !tableName.startsWith("#") && 
                !tableName.equals("INFORMATION_SCHEMA")) {
                tables.add(tableName);
            }
        }
        
        return tables;
    }
    
    private String determineJoinPattern(String sql) {
        if (sql.contains("INNER JOIN")) {
            return "INNER_JOIN";
        } else if (sql.contains("LEFT JOIN") || sql.contains("LEFT OUTER JOIN")) {
            return "LEFT_JOIN";
        } else if (sql.contains("RIGHT JOIN") || sql.contains("RIGHT OUTER JOIN")) {
            return "RIGHT_JOIN";
        } else if (sql.contains("FULL JOIN") || sql.contains("FULL OUTER JOIN")) {
            return "FULL_JOIN";
        } else if (sql.contains("JOIN")) {
            return "IMPLICIT_JOIN";
        } else {
            return "SINGLE_TABLE";
        }
    }
    
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
            
            // Get top queries by execution
            System.out.println("\n=== Top Queries by Execution Count ===");
            List<QueryStoreQuery> topQueries = connector.getTopQueriesByExecution();
            
            for (int i = 0; i < Math.min(5, topQueries.size()); i++) {
                QueryStoreQuery query = topQueries.get(i);
                System.out.printf("\n#%d - Query ID: %s%n", i + 1, query.getQueryId());
                System.out.printf("  Executions: %,d%n", query.getExecutionCount());
                System.out.printf("  Avg Duration: %.2f ms%n", query.getAvgDurationMs());
                System.out.printf("  Tables: %s%n", query.getTablesAccessed());
                System.out.printf("  Join Pattern: %s%n", query.getJoinPattern());
                
                String sqlPreview = query.getSqlText().length() > 150 
                    ? query.getSqlText().substring(0, 150) + "..."
                    : query.getSqlText();
                System.out.printf("  SQL: %s%n", sqlPreview.replaceAll("\\s+", " "));
            }
            
            // Analyze Customer+Profile patterns
            long customerProfileQueries = topQueries.stream()
                .filter(q -> q.getTablesAccessed() != null)
                .filter(q -> q.getTablesAccessed().contains("Customers") && 
                            q.getTablesAccessed().contains("CustomerProfiles"))
                .count();
                
            System.out.printf("\n=== Analysis Results ===%n");
            System.out.printf("Customer+Profile queries found: %d%n", customerProfileQueries);
            
            if (customerProfileQueries > 0) {
                System.out.println("✓ SUCCESS: Customer+Profile always-together pattern detected!");
                System.out.println("  This indicates a HIGH priority DynamoDB migration candidate");
            } else {
                System.out.println("⚠ No Customer+Profile patterns found in top queries");
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}