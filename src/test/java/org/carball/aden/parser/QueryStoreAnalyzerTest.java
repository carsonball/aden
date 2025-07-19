package org.carball.aden.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.carball.aden.model.query.QueryStoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryStoreAnalyzer - validates analysis logic and JSON output structure.
 */
class QueryStoreAnalyzerTest {
    
    private QueryStoreAnalyzer analyzer;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        analyzer = new QueryStoreAnalyzer();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testAnalyzeCustomerProfileQueries(@TempDir Path tempDir) throws IOException {
        // Create test queries simulating Customer+Profile pattern
        List<QueryStoreQuery> queries = Arrays.asList(
            createTestQuery("1", 
                "(@p__linq__0 int)SELECT [Extent1].[Id], [Extent2].[Address] " +
                "FROM [dbo].[Customer] AS [Extent1] " +
                "LEFT OUTER JOIN [dbo].[CustomerProfile] AS [Extent2] ON [Extent1].[Id] = [Extent2].[CustomerId]",
                1000, 0.5, 0.4, 4.0),
            createTestQuery("2",
                "UPDATE [dbo].[CustomerProfile] SET [PhoneNumber] = @0 WHERE ([CustomerId] = @1)",
                50, 0.3, 0.2, 2.0)
        );
        
        // Analyze and export
        File outputFile = tempDir.resolve("test_analysis.json").toFile();
        analyzer.analyzeAndExport(queries, outputFile.getAbsolutePath(), "TestDB");
        
        // Verify file created
        assertTrue(outputFile.exists());
        
        // Parse and verify JSON structure
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = objectMapper.readValue(outputFile, Map.class);
        
        // Verify metadata
        assertEquals("TestDB", analysis.get("database"));
        assertEquals("QUERY_STORE_PRODUCTION_METRICS", analysis.get("analysisType"));
        assertEquals(2, analysis.get("totalQueriesAnalyzed"));
        
        // Verify qualified metrics
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) analysis.get("qualifiedMetrics");
        assertNotNull(metrics);
        
        // Check operation breakdown
        @SuppressWarnings("unchecked")
        Map<String, Object> operations = (Map<String, Object>) metrics.get("operationBreakdown");
        assertEquals(1000, ((Number) operations.get("SELECT")).intValue());
        assertEquals(50, ((Number) operations.get("UPDATE")).intValue());
        
        // Check read/write ratio
        double readWriteRatio = (Double) metrics.get("readWriteRatio");
        assertEquals(20.0, readWriteRatio, 0.01);
        assertTrue((Boolean) metrics.get("isReadHeavy"));
        
        // Check table co-access patterns
        @SuppressWarnings("unchecked")
        Map<String, Object> tablePatterns = (Map<String, Object>) metrics.get("tableAccessPatterns");
        assertTrue((Boolean) tablePatterns.get("hasStrongCoAccessPatterns"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> combinations = (List<Map<String, Object>>) tablePatterns.get("frequentTableCombinations");
        assertEquals(1, combinations.size());
        
        Map<String, Object> combo = combinations.get(0);
        @SuppressWarnings("unchecked")
        List<String> tables = (List<String>) combo.get("tables");
        assertTrue(tables.contains("Customer"));
        assertTrue(tables.contains("CustomerProfile"));
        assertEquals(1000L, ((Number) combo.get("totalExecutions")).longValue());
    }
    
    @Test
    void testPerformanceAnalysis(@TempDir Path tempDir) throws IOException {
        // Create queries with varying performance characteristics
        List<QueryStoreQuery> queries = Arrays.asList(
            createTestQuery("1", "SELECT * FROM Products", 100, 150.0, 120.0, 1000.0), // Slow query
            createTestQuery("2", "SELECT * FROM Orders WHERE Id = @0", 500, 5.0, 3.0, 10.0) // Fast query
        );
        
        File outputFile = tempDir.resolve("perf_analysis.json").toFile();
        analyzer.analyzeAndExport(queries, outputFile.getAbsolutePath(), "TestDB");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = objectMapper.readValue(outputFile, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) analysis.get("qualifiedMetrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> performance = (Map<String, Object>) metrics.get("performanceCharacteristics");
        
        assertEquals(1L, ((Number) performance.get("slowQueryCount")).longValue());
        assertTrue((Boolean) performance.get("hasPerformanceIssues"));
        
        double avgDuration = (Double) performance.get("avgQueryDurationMs");
        assertTrue(avgDuration > 20); // Should be weighted average
    }
    
    @Test
    void testAccessPatternDetection(@TempDir Path tempDir) throws IOException {
        List<QueryStoreQuery> queries = Arrays.asList(
            createTestQuery("1", "SELECT * FROM Users WHERE Id = @0", 100, 1.0, 0.8, 2.0),
            createTestQuery("2", "SELECT * FROM Products p JOIN Categories c ON p.CategoryId = c.Id", 50, 5.0, 4.0, 10.0),
            createTestQuery("3", "SELECT COUNT(*) FROM Orders WHERE Date BETWEEN @0 AND @1", 30, 10.0, 8.0, 100.0)
        );
        
        File outputFile = tempDir.resolve("patterns_analysis.json").toFile();
        analyzer.analyzeAndExport(queries, outputFile.getAbsolutePath(), "TestDB");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = objectMapper.readValue(outputFile, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) analysis.get("qualifiedMetrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> patterns = (Map<String, Object>) metrics.get("accessPatternDistribution");
        
        assertEquals(1L, ((Number) patterns.get("KEY_LOOKUP")).longValue());
        assertEquals(1L, ((Number) patterns.get("JOIN_QUERY")).longValue());
        assertEquals(1L, ((Number) patterns.get("RANGE_SCAN")).longValue());
    }
    
    @Test
    void testEmptyQueryList(@TempDir Path tempDir) throws IOException {
        List<QueryStoreQuery> emptyQueries = Arrays.asList();
        
        File outputFile = tempDir.resolve("empty_analysis.json").toFile();
        analyzer.analyzeAndExport(emptyQueries, outputFile.getAbsolutePath(), "TestDB");
        
        // Should not throw exception
        assertTrue(outputFile.exists());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = objectMapper.readValue(outputFile, Map.class);
        assertEquals(0, analysis.get("totalQueriesAnalyzed"));
    }
    
    private QueryStoreQuery createTestQuery(String id, String sql, long executions, 
                                          double duration, double cpu, double reads) {
        QueryStoreQuery query = new QueryStoreQuery();
        query.setQueryId(id);
        query.setSqlText(sql);
        query.setExecutionCount(executions);
        query.setAvgDurationMs(duration);
        query.setAvgCpuTimeMs(cpu);
        query.setAvgLogicalReads(reads);
        return query;
    }
}