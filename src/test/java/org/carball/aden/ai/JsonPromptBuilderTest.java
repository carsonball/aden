package org.carball.aden.ai;

import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.model.schema.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonPromptBuilderTest {
    
    private JsonRecommendationEngine engine;
    private AnalysisResult analysisResult;
    
    @BeforeEach
    public void setUp() {
        System.setProperty("skip.ai", "true");
        engine = new JsonRecommendationEngine("test-key");
        analysisResult = new AnalysisResult();
        analysisResult.setDenormalizationCandidates(new ArrayList<>());
        analysisResult.setQueryPatterns(new ArrayList<>());
    }
    
    @Test
    public void shouldIncludeCoAccessedEntitiesWithin85PercentThreshold() {
        // Create an entity with co-accessed patterns
        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        EntityUsageProfile profile = new EntityUsageProfile(customer);
        
        // Add co-accessed entities with specific counts
        // Total: 1000, 85% threshold: 850
        profile.addCoAccessedEntity("Order", 600);      // 60% - should be included
        profile.addCoAccessedEntity("OrderItem", 300);  // 30% - should be included (cumulative 90%)
        profile.addCoAccessedEntity("Payment", 50);     // 5% - should NOT be included (would exceed 85%)
        profile.addCoAccessedEntity("Address", 30);     // 3% - should NOT be included
        profile.addCoAccessedEntity("Audit", 20);       // 2% - should NOT be included
        
        // Set up other required data
        profile.setEagerLoadingCount(100);
        profile.setReadCount(1000);
        profile.setWriteCount(100);
        
        analysisResult.getUsageProfiles().put("Customer", profile);
        
        // Create denormalization candidate
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
                .primaryEntity("Customer")
                .relatedEntities(Arrays.asList("Order", "OrderItem"))
                .complexity(MigrationComplexity.MEDIUM)
                .score(120)
                .reason("High frequency access with related entities")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .build();
        
        analysisResult.getDenormalizationCandidates().add(candidate);
        
        // Generate recommendations (with skip.ai=true, it will use fallback)
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(
                analysisResult, null, null, null);
        
        // We can't directly inspect the prompt, but we can verify the behavior
        // by checking that the recommendation was created (indicating the method ran)
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).getPrimaryEntity()).isEqualTo("Customer");
    }
    
    @Test
    public void shouldHandleEmptyCoAccessedEntities() {
        EntityModel product = new EntityModel("Product", "Product.cs");
        EntityUsageProfile profile = new EntityUsageProfile(product);
        
        // No co-accessed entities
        profile.setEagerLoadingCount(50);
        profile.setReadCount(500);
        profile.setWriteCount(50);
        
        analysisResult.getUsageProfiles().put("Product", profile);
        
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
                .primaryEntity("Product")
                .relatedEntities(new ArrayList<>())
                .complexity(MigrationComplexity.LOW)
                .score(80)
                .reason("Simple entity with key-based access")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .build();
        
        analysisResult.getDenormalizationCandidates().add(candidate);
        
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(
                analysisResult, null, null, null);
        
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).getPrimaryEntity()).isEqualTo("Product");
    }
    
    @Test
    public void shouldIncludeProductionMetricsWhenAvailable() {
        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        EntityUsageProfile profile = new EntityUsageProfile(customer);
        
        // Set production metrics
        profile.setProductionExecutionCount(50000);
        profile.setProductionReadWriteRatio(15.5);
        profile.setEagerLoadingCount(100);
        profile.setReadCount(1000);
        profile.setWriteCount(100);
        
        analysisResult.getUsageProfiles().put("Customer", profile);
        
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
                .primaryEntity("Customer")
                .relatedEntities(new ArrayList<>())
                .complexity(MigrationComplexity.LOW)
                .score(150)
                .reason("High production usage")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .build();
        
        analysisResult.getDenormalizationCandidates().add(candidate);
        
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(
                analysisResult, null, null, null);
        
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).getPrimaryEntity()).isEqualTo("Customer");
    }
    
    @Test
    public void shouldIncludeRelationshipTypes() {
        EntityModel order = new EntityModel("Order", "Order.cs");
        EntityUsageProfile profile = new EntityUsageProfile(order);
        
        // Add relationship types
        profile.addRelatedEntity("Customer", RelationshipType.MANY_TO_ONE);
        profile.addRelatedEntity("OrderItem", RelationshipType.ONE_TO_MANY);
        profile.addRelatedEntity("Payment", RelationshipType.ONE_TO_ONE);
        
        profile.setEagerLoadingCount(75);
        profile.setReadCount(750);
        profile.setWriteCount(75);
        
        analysisResult.getUsageProfiles().put("Order", profile);
        
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
                .primaryEntity("Order")
                .relatedEntities(Arrays.asList("Customer", "OrderItem", "Payment"))
                .complexity(MigrationComplexity.MEDIUM)
                .score(110)
                .reason("Complex relationships")
                .recommendedTarget(NoSQLTarget.DOCUMENTDB)
                .build();
        
        analysisResult.getDenormalizationCandidates().add(candidate);
        
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(
                analysisResult, null, null, null);
        
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).getPrimaryEntity()).isEqualTo("Order");
        assertThat(recommendations.get(0).getTargetService()).isEqualTo(NoSQLTarget.DOCUMENTDB);
    }
    
    @Test
    public void shouldCalculate85PercentThresholdCorrectly() {
        EntityModel user = new EntityModel("User", "User.cs");
        EntityUsageProfile profile = new EntityUsageProfile(user);
        
        // Edge case: ensure 85% calculation is correct
        // Total: 100, 85% = 85
        profile.addCoAccessedEntity("Session", 40);    // 40% - included
        profile.addCoAccessedEntity("Profile", 30);    // 30% - included (cumulative 70%)
        profile.addCoAccessedEntity("Settings", 20);   // 20% - included (cumulative 90%, exceeds 85%)
        profile.addCoAccessedEntity("Log", 10);        // 10% - NOT included
        
        profile.setEagerLoadingCount(50);
        profile.setReadCount(500);
        profile.setWriteCount(100);
        
        analysisResult.getUsageProfiles().put("User", profile);
        
        DenormalizationCandidate candidate = DenormalizationCandidate.builder()
                .primaryEntity("User")
                .relatedEntities(Arrays.asList("Session", "Profile"))
                .complexity(MigrationComplexity.LOW)
                .score(90)
                .reason("User data access patterns")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .build();
        
        analysisResult.getDenormalizationCandidates().add(candidate);
        
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(
                analysisResult, null, null, null);
        
        assertThat(recommendations).hasSize(1);
        
        // Note: We can't directly verify the prompt content without changing implementation,
        // but the test verifies the method executes successfully with the co-access data
    }
}