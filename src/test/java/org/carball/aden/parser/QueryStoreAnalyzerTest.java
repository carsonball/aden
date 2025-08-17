package org.carball.aden.parser;

import org.carball.aden.model.query.*;
import org.carball.aden.config.ThresholdConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryStoreAnalyzer - validates analysis logic and JSON output structure.
 */
class QueryStoreAnalyzerTest {
    
    private ThresholdConfig thresholdConfig;
    
    @BeforeEach
    void setUp() {
        thresholdConfig = ThresholdConfig.createDiscoveryDefaults();
    }
    
    @Test
    void testAnalyzeCustomerProfileQueries() {
        // Create test queries simulating Customer+Profile pattern
        List<QueryStoreQuery> queries = Arrays.asList(
            new QueryStoreQuery("1",
                "(@p__linq__0 int)SELECT [Extent1].[Id], [Extent2].[Address] " +
                "FROM [dbo].[Customer] AS [Extent1] " +
                "LEFT OUTER JOIN [dbo].[CustomerProfile] AS [Extent2] ON [Extent1].[Id] = [Extent2].[CustomerId]",
                1000, 0.5, 0.4, 4.0),
            new QueryStoreQuery("2",
                "UPDATE [dbo].[CustomerProfile] SET [PhoneNumber] = @0 WHERE ([CustomerId] = @1)",
                50, 0.3, 0.2, 2.0)
        );
        
        // Analyze
        QueryStoreAnalysis analysis = QueryStoreAnalyzer.analyze(queries, "TestDB", thresholdConfig);
        
        // Verify metadata
        assertEquals("TestDB", analysis.database());
        assertEquals("QUERY_STORE_PRODUCTION_METRICS", analysis.analysisType());
        assertEquals(2, analysis.totalQueriesAnalyzed());
        
        // Verify qualified metrics
        QualifiedMetrics metrics = analysis.qualifiedMetrics();
        assertNotNull(metrics);
        
        // Check operation breakdown
        Map<String, Long> operations = metrics.getOperationBreakdown();
        assertEquals(1000L, operations.get("SELECT").longValue());
        assertEquals(50L, operations.get("UPDATE").longValue());
        
        // Check read/write ratio
        double readWriteRatio = metrics.getReadWriteRatio();
        assertEquals(20.0, readWriteRatio, 0.01);
        assertTrue(metrics.isReadHeavy());
        
        // Check table co-access patterns
        TableAccessPatterns tablePatterns = metrics.getTableAccessPatterns();
        assertTrue(tablePatterns.hasStrongCoAccessPatterns());
        
        List<TableCombination> combinations = tablePatterns.frequentTableCombinations();
        assertEquals(1, combinations.size());
        
        TableCombination combo = combinations.get(0);
        List<String> tables = combo.getTables();
        assertTrue(tables.contains("Customer"));
        assertTrue(tables.contains("CustomerProfile"));
        assertEquals(1000L, combo.getTotalExecutions());
    }
    
    @Test
    void testPerformanceAnalysis() {
        // Create queries with varying performance characteristics
        List<QueryStoreQuery> queries = Arrays.asList(
            new QueryStoreQuery("1", "SELECT * FROM Products", 100, 150.0, 120.0, 1000.0), // Slow query
            new QueryStoreQuery("2", "SELECT * FROM Orders WHERE Id = @0", 500, 5.0, 3.0, 10.0) // Fast query
        );
        
        QueryStoreAnalysis analysis = QueryStoreAnalyzer.analyze(queries, "TestDB", thresholdConfig);
        
        QualifiedMetrics metrics = analysis.qualifiedMetrics();
        PerformanceCharacteristics performance = metrics.getPerformanceCharacteristics();
        
        assertEquals(1L, performance.getSlowQueryCount());
        assertTrue(performance.isHasPerformanceIssues());
        
        double avgDuration = performance.getAvgQueryDurationMs();
        assertTrue(avgDuration > 20); // Should be weighted average
    }
    
    @Test
    void testAccessPatternDetection() {
        List<QueryStoreQuery> queries = Arrays.asList(
            new QueryStoreQuery("1", "SELECT * FROM Users WHERE Id = @0", 100, 1.0, 0.8, 2.0),
            new QueryStoreQuery("2", "SELECT * FROM Products p JOIN Categories c ON p.CategoryId = c.Id", 50, 5.0, 4.0, 10.0),
            new QueryStoreQuery("3", "SELECT COUNT(*) FROM Orders WHERE Date BETWEEN @0 AND @1", 30, 10.0, 8.0, 100.0)
        );
        
        QueryStoreAnalysis analysis = QueryStoreAnalyzer.analyze(queries, "TestDB", thresholdConfig);
        
        QualifiedMetrics metrics = analysis.qualifiedMetrics();
        Map<String, Long> patterns = metrics.getAccessPatternDistribution();
        
        assertEquals(1L, patterns.get("KEY_LOOKUP").longValue());
        assertEquals(1L, patterns.get("JOIN_QUERY").longValue());
        assertEquals(1L, patterns.get("RANGE_SCAN").longValue());
    }
    
    @Test
    void testEmptyQueryList() {
        List<QueryStoreQuery> emptyQueries = List.of();
        
        QueryStoreAnalysis analysis = QueryStoreAnalyzer.analyze(emptyQueries, "TestDB", thresholdConfig);
        
        // Should not throw exception
        assertNotNull(analysis);
        assertEquals(0, analysis.totalQueriesAnalyzed());
    }
}