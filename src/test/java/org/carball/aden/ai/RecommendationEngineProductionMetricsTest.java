package org.carball.aden.ai;

import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.analysis.DenormalizationCandidate;
import org.carball.aden.model.analysis.EntityUsageProfile;
import org.carball.aden.model.analysis.MigrationComplexity;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.model.analysis.NoSQLTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RecommendationEngine with production metrics from Query Store.
 */
class RecommendationEngineProductionMetricsTest {
    
    private RecommendationEngine engine;
    
    @BeforeEach
    void setUp() {
        // Skip AI mode for testing
        System.setProperty("skip.ai", "true");
        engine = new RecommendationEngine("dummy-key");
    }
    
    @Test
    void testRecommendationWithProductionMetrics() {
        // Create test analysis result
        AnalysisResult analysis = new AnalysisResult();
        analysis.setDenormalizationCandidates(new ArrayList<>());
        
        // Add Customer entity profile
        EntityModel customerEntity = new EntityModel("Customer", "Customer.cs");
        
        EntityUsageProfile customerProfile = new EntityUsageProfile(customerEntity);
        customerProfile.incrementEagerLoadingCount(10);
        customerProfile.getAlwaysLoadedWithEntities().add("CustomerProfile");
        analysis.getUsageProfiles().put("Customer", customerProfile);
        
        // Add denormalization candidate
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
            .primaryEntity("Customer")
            .relatedEntities(Arrays.asList("CustomerProfile"))
            .recommendedTarget(NoSQLTarget.DYNAMODB)
            .reason("Always loaded together pattern")
            .complexity(MigrationComplexity.LOW)
            .score(120)
            .build();
        analysis.getDenormalizationCandidates().add(candidate);
        
        // Create production metrics matching our Query Store analysis structure
        Map<String, Object> productionMetrics = new HashMap<>();
        Map<String, Object> qualifiedMetrics = new HashMap<>();
        
        // Operation breakdown
        Map<String, Object> operations = new HashMap<>();
        operations.put("SELECT", 1021);
        operations.put("UPDATE", 21);
        qualifiedMetrics.put("operationBreakdown", operations);
        
        // Read/write ratio
        qualifiedMetrics.put("readWriteRatio", 48.6);
        qualifiedMetrics.put("isReadHeavy", true);
        qualifiedMetrics.put("totalExecutions", 1042);
        
        // Table access patterns
        Map<String, Object> tablePatterns = new HashMap<>();
        tablePatterns.put("hasStrongCoAccessPatterns", true);
        
        List<Map<String, Object>> combinations = new ArrayList<>();
        Map<String, Object> combo = new HashMap<>();
        combo.put("tables", Arrays.asList("Customer", "CustomerProfile"));
        combo.put("totalExecutions", 1021);
        combo.put("executionPercentage", 98.0);
        combinations.add(combo);
        
        tablePatterns.put("frequentTableCombinations", combinations);
        qualifiedMetrics.put("tableAccessPatterns", tablePatterns);
        
        // Performance characteristics
        Map<String, Object> performance = new HashMap<>();
        performance.put("avgQueryDurationMs", 0.05);
        performance.put("avgLogicalReads", 3);
        performance.put("hasPerformanceIssues", false);
        qualifiedMetrics.put("performanceCharacteristics", performance);
        
        // Access patterns
        Map<String, Object> accessPatterns = new HashMap<>();
        accessPatterns.put("KEY_LOOKUP", 1);
        accessPatterns.put("JOIN_QUERY", 1);
        qualifiedMetrics.put("accessPatternDistribution", accessPatterns);
        
        productionMetrics.put("qualifiedMetrics", qualifiedMetrics);
        
        // Generate recommendations with production metrics
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(
            analysis, null, null, productionMetrics
        );
        
        // Verify recommendation generated
        assertNotNull(recommendations);
        assertEquals(1, recommendations.size());
        
        NoSQLRecommendation rec = recommendations.get(0);
        assertEquals("Customer", rec.getPrimaryEntity());
        assertEquals(NoSQLTarget.DYNAMODB, rec.getTargetService());
        
        // The fallback recommendation should still work
        assertNotNull(rec.getPartitionKey());
        assertNotNull(rec.getTableName());
    }
    
    @Test
    void testProductionMetricsInPrompt() {
        // This test would verify the prompt building with production metrics
        // In a real scenario with AI enabled, we'd mock the OpenAI service
        // and verify the prompt contains the production metrics
        
        // For now, we just verify the method accepts production metrics
        AnalysisResult analysis = new AnalysisResult();
        analysis.setDenormalizationCandidates(new ArrayList<>());
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
            .primaryEntity("TestEntity")
            .relatedEntities(new ArrayList<>())
            .recommendedTarget(NoSQLTarget.DYNAMODB)
            .complexity(MigrationComplexity.MEDIUM)
            .score(80)
            .build();
        analysis.getDenormalizationCandidates().add(candidate);
        
        Map<String, Object> productionMetrics = new HashMap<>();
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            engine.generateRecommendations(analysis, null, null, productionMetrics);
        });
    }
}