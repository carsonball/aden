package org.carball.aden.parser;

import org.carball.aden.model.query.QueryStoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryStoreConnector - pure data extraction from SQL Server Query Store.
 * Note: These tests require Docker SQL Server with TestEcommerceApp database running.
 */
class QueryStoreConnectorTest {
    
    private QueryStoreConnector connector;
    
    @BeforeEach
    void setUp() {
        // Use Docker SQL Server connection
        String connectionString = QueryStoreConnector.createTestAppConnectionString();
        connector = new QueryStoreConnector(connectionString);
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testQueryStoreEnabled() throws SQLException {
        assertTrue(connector.isQueryStoreEnabled(), 
                  "Query Store should be enabled on TestEcommerceApp database");
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testGetQueryCount() throws SQLException {
        int queryCount = connector.getQueryStoreQueryCount();
        assertTrue(queryCount >= 0, 
                  "Query count should be non-negative");
        System.out.println("Total queries in Query Store: " + queryCount);
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testGetAllQueries() throws SQLException {
        List<QueryStoreQuery> queries = connector.getAllQueries();
        
        assertNotNull(queries);
        // Don't assert on emptiness - Query Store might be empty
        
        // Print extracted data for verification
        System.out.println("\n=== Extracted Query Store Data ===");
        System.out.printf("Total queries extracted: %d%n", queries.size());
        
        for (int i = 0; i < Math.min(5, queries.size()); i++) {
            QueryStoreQuery query = queries.get(i);
            System.out.printf("\nQuery #%d:%n", i + 1);
            System.out.printf("  Query ID: %s%n", query.getQueryId());
            System.out.printf("  Executions: %d%n", query.getExecutionCount());
            System.out.printf("  Avg Duration: %.2f ms%n", query.getAvgDurationMs());
            System.out.printf("  Avg CPU: %.2f ms%n", query.getAvgCpuTimeMs());
            System.out.printf("  Avg Logical Reads: %.0f%n", query.getAvgLogicalReads());
            
            String sqlPreview = query.getSqlText().length() > 100 
                ? query.getSqlText().substring(0, 100) + "..."
                : query.getSqlText();
            System.out.printf("  SQL: %s%n", sqlPreview.replaceAll("\\s+", " "));
        }
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testQueryDataIntegrity() throws SQLException {
        List<QueryStoreQuery> queries = connector.getAllQueries();
        
        // Verify all queries have required fields
        for (QueryStoreQuery query : queries) {
            assertNotNull(query.getQueryId(), "Query ID should not be null");
            assertNotNull(query.getSqlText(), "SQL text should not be null");
            assertTrue(query.getExecutionCount() >= 0, "Execution count should be non-negative");
            assertTrue(query.getAvgDurationMs() >= 0, "Average duration should be non-negative");
            assertTrue(query.getAvgCpuTimeMs() >= 0, "Average CPU time should be non-negative");
            assertTrue(query.getAvgLogicalReads() >= 0, "Average logical reads should be non-negative");
            
            // Verify removed fields are null (no analysis in connector)
            assertNull(query.getTablesAccessed(), "Tables accessed should be null (analysis removed)");
            assertNull(query.getJoinPattern(), "Join pattern should be null (analysis removed)");
        }
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testConnectionStringCreation() {
        String connectionString = QueryStoreConnector.createTestAppConnectionString();
        
        assertNotNull(connectionString);
        assertTrue(connectionString.contains("localhost:1433"), "Should connect to Docker SQL Server");
        assertTrue(connectionString.contains("TestEcommerceApp"), "Should target TestEcommerceApp database");
        assertTrue(connectionString.contains("sa"), "Should use SQL Server authentication");
    }
    
    /**
     * Checks if Docker SQL Server with TestEcommerceApp database is available for testing.
     */
    static boolean isDatabaseAvailable() {
        try {
            String connectionString = QueryStoreConnector.createTestAppConnectionString();
            QueryStoreConnector testConnector = new QueryStoreConnector(connectionString);
            return testConnector.isQueryStoreEnabled();
        } catch (Exception e) {
            System.out.println("Docker SQL Server not available for testing: " + e.getMessage());
            System.out.println("Run 'docker-compose up -d' to start test environment");
            return false;
        }
    }
}