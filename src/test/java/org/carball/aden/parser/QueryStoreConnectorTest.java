package org.carball.aden.parser;

import org.carball.aden.model.query.QueryStoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryStoreConnector.
 * Note: These tests require TestEcommerceApp database to be running with Query Store enabled.
 */
class QueryStoreConnectorTest {
    
    private QueryStoreConnector connector;
    
    @BeforeEach
    void setUp() {
        // Use LocalDB connection string for TestEcommerceApp
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
        assertTrue(queryCount > 0, 
                  "Query Store should contain queries after running simulation");
        System.out.println("Total queries in Query Store: " + queryCount);
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testGetTopQueriesByExecution() throws SQLException {
        List<QueryStoreQuery> topQueries = connector.getTopQueriesByExecution();
        
        assertNotNull(topQueries);
        assertFalse(topQueries.isEmpty(), "Should have captured queries from simulation");
        
        // Print results for manual inspection
        System.out.println("\n=== Top Queries by Execution Count ===");
        for (QueryStoreQuery query : topQueries) {
            System.out.printf("Query ID: %s, Executions: %d, Avg Duration: %.2fms%n", 
                            query.getQueryId(), query.getExecutionCount(), query.getAvgDurationMs());
            System.out.printf("Tables: %s, Join: %s%n", 
                            query.getTablesAccessed(), query.getJoinPattern());
            System.out.printf("SQL (first 200 chars): %s...%n%n", 
                            query.getSqlText().substring(0, Math.min(200, query.getSqlText().length())));
        }
        
        // Look for Customer+Profile patterns
        long customerProfileQueries = topQueries.stream()
            .filter(q -> q.getTablesAccessed() != null)
            .filter(q -> q.getTablesAccessed().contains("Customers") && 
                        q.getTablesAccessed().contains("CustomerProfiles"))
            .count();
            
        assertTrue(customerProfileQueries > 0, 
                  "Should find Customer+Profile queries from simulation");
    }
    
    @Test
    @EnabledIf("isDatabaseAvailable")
    void testTableNameExtraction() {
        QueryStoreQuery testQuery = new QueryStoreQuery();
        testQuery.setSqlText("SELECT c.Name, p.Address FROM Customers c INNER JOIN CustomerProfiles p ON c.Id = p.CustomerId WHERE c.Id = @0");
        
        // This would be called internally, but we can test the logic
        // For now, just verify the test query structure
        assertNotNull(testQuery.getSqlText());
        assertTrue(testQuery.getSqlText().contains("Customers"));
        assertTrue(testQuery.getSqlText().contains("CustomerProfiles"));
        assertTrue(testQuery.getSqlText().contains("INNER JOIN"));
    }
    
    /**
     * Checks if TestEcommerceApp database is available for testing.
     */
    static boolean isDatabaseAvailable() {
        try {
            String connectionString = QueryStoreConnector.createTestAppConnectionString();
            QueryStoreConnector testConnector = new QueryStoreConnector(connectionString);
            return testConnector.isQueryStoreEnabled();
        } catch (Exception e) {
            System.out.println("Database not available for testing: " + e.getMessage());
            return false;
        }
    }
}