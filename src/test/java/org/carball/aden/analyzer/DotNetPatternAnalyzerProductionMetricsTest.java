package org.carball.aden.analyzer;

import org.carball.aden.config.MigrationThresholds;
import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.analysis.DenormalizationCandidate;
import org.carball.aden.model.analysis.EntityUsageProfile;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.model.schema.Relationship;
import org.carball.aden.model.schema.RelationshipType;
import org.carball.aden.model.schema.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class DotNetPatternAnalyzerProductionMetricsTest {

    private DotNetPatternAnalyzer analyzer;
    private DatabaseSchema schema;
    private List<EntityModel> entities;
    private List<QueryPattern> queryPatterns;
    private Map<String, String> dbSetMapping;

    @BeforeEach
    void setUp() {
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .highFrequencyThreshold(50)
                .mediumFrequencyThreshold(20)
                .productionCoAccessThreshold(500)
                .highProductionExecutionThreshold(1000)
                .build();
        analyzer = new DotNetPatternAnalyzer(thresholds);

        // Create test schema
        schema = new DatabaseSchema();
        Table customerTable = new Table("Customer");
        Table orderTable = new Table("Order");
        Table customerProfileTable = new Table("CustomerProfile");
        
        schema.addTable(customerTable);
        schema.addTable(orderTable);
        schema.addTable(customerProfileTable);
        
        schema.addRelationship(Relationship.builder()
                .name("FK_Order_Customer")
                .fromTable("Order")
                .fromColumn("CustomerId")
                .toTable("Customer")
                .toColumn("Id")
                .type(RelationshipType.MANY_TO_ONE)
                .build());
        schema.addRelationship(Relationship.builder()
                .name("FK_CustomerProfile_Customer")
                .fromTable("CustomerProfile")
                .fromColumn("CustomerId")
                .toTable("Customer")
                .toColumn("Id")
                .type(RelationshipType.ONE_TO_ONE)
                .build());

        // Create test entities with table mappings
        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        customer.setTableName("Customer");
        
        EntityModel order = new EntityModel("Order", "Order.cs");
        order.setTableName("Order");
        
        EntityModel customerProfile = new EntityModel("CustomerProfile", "CustomerProfile.cs");
        customerProfile.setTableName("CustomerProfile");
        
        entities = new ArrayList<>(Arrays.asList(customer, order, customerProfile));

        // Create test query patterns
        queryPatterns = new ArrayList<>();
        QueryPattern pattern1 = new QueryPattern("SingleEntity", "Customer", 10, "Repository.cs");
        pattern1.setQueryType(QueryType.SINGLE_ENTITY);
        queryPatterns.add(pattern1);

        dbSetMapping = new HashMap<>();
        dbSetMapping.put("Customers", "Customer");
        dbSetMapping.put("Orders", "Order");
        dbSetMapping.put("CustomerProfiles", "CustomerProfile");
    }

    @Test
    void testProductionMetricsInfluenceCandidateSelection() {
        // Create production metrics showing CustomerProfile is frequently co-accessed
        Map<String, Object> productionMetrics = createProductionMetrics();

        // Analyze without production metrics first
        AnalysisResult resultWithoutMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping);
        
        // Analyze with production metrics
        AnalysisResult resultWithMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Without production metrics, CustomerProfile should not be a candidate
        assertThat(resultWithoutMetrics.getDenormalizationCandidates())
                .extracting(DenormalizationCandidate::getPrimaryEntity)
                .doesNotContain("CustomerProfile");

        // With production metrics, CustomerProfile should be a candidate
        assertThat(resultWithMetrics.getDenormalizationCandidates())
                .extracting(DenormalizationCandidate::getPrimaryEntity)
                .contains("CustomerProfile");

        // The candidate should include Customer as a related entity due to co-access
        DenormalizationCandidate profileCandidate = resultWithMetrics.getDenormalizationCandidates()
                .stream()
                .filter(c -> c.getPrimaryEntity().equals("CustomerProfile"))
                .findFirst()
                .orElse(null);
        
        assertThat(profileCandidate).isNotNull();
        assertThat(profileCandidate.getRelatedEntities()).contains("Customer");
        assertThat(profileCandidate.getScore()).isGreaterThan(50); // Should have good score from production data
    }

    @Test
    void testProductionMetricsEnhanceExistingCandidates() {
        // Add query pattern that makes Customer a candidate
        QueryPattern pattern2 = new QueryPattern("EagerLoading", "Customer.Orders", 100, "Repository.cs");
        pattern2.setQueryType(QueryType.EAGER_LOADING);
        queryPatterns.add(pattern2);

        Map<String, Object> productionMetrics = createProductionMetrics();

        AnalysisResult resultWithoutMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping);
        
        AnalysisResult resultWithMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Find Customer candidate in both results
        DenormalizationCandidate customerWithoutMetrics = findCandidate(
                resultWithoutMetrics.getDenormalizationCandidates(), "Customer");
        DenormalizationCandidate customerWithMetrics = findCandidate(
                resultWithMetrics.getDenormalizationCandidates(), "Customer");

        assertThat(customerWithoutMetrics).isNotNull();
        assertThat(customerWithMetrics).isNotNull();

        // With production metrics, score should be higher
        assertThat(customerWithMetrics.getScore())
                .isGreaterThan(customerWithoutMetrics.getScore());

        // With production metrics, should include CustomerProfile as related entity
        assertThat(customerWithMetrics.getRelatedEntities())
                .contains("CustomerProfile");
    }

    @Test
    void testTableNameMappingWithProductionMetrics() {
        // Test that table name mappings are properly used
        EntityModel entityWithCustomTable = new EntityModel("CustomerEntity", "CustomerEntity.cs");
        entityWithCustomTable.setTableName("Customers"); // Different from entity name
        entities.add(entityWithCustomTable);
        
        // Also add table to schema
        schema.addTable(new Table("Customers"));

        Map<String, Object> productionMetrics = createProductionMetricsWithCustomTableName();

        AnalysisResult result = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Check that usage profiles have production data
        EntityUsageProfile profile = result.getUsageProfiles().get("CustomerEntity");
        assertThat(profile).isNotNull();
        // The production execution count comes from co-access patterns, not individual queries
        // But we should have production read/write ratio from the query analysis
        assertThat(profile.getProductionReadWriteRatio()).isEqualTo(1500.0); // All reads, no writes
    }

    private Map<String, Object> createProductionMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Qualified metrics
        Map<String, Object> qualifiedMetrics = new HashMap<>();
        
        // Table access patterns
        Map<String, Object> tablePatterns = new HashMap<>();
        List<Map<String, Object>> frequentCombinations = new ArrayList<>();
        
        // Customer and CustomerProfile are frequently accessed together
        Map<String, Object> combo1 = new HashMap<>();
        combo1.put("tables", Arrays.asList("Customer", "CustomerProfile"));
        combo1.put("totalExecutions", 5000L);
        combo1.put("executionPercentage", 45.0);
        frequentCombinations.add(combo1);
        
        tablePatterns.put("frequentTableCombinations", frequentCombinations);
        tablePatterns.put("hasStrongCoAccessPatterns", true);
        qualifiedMetrics.put("tableAccessPatterns", tablePatterns);
        
        metrics.put("qualifiedMetrics", qualifiedMetrics);
        
        // Individual queries
        List<Map<String, Object>> queries = new ArrayList<>();
        
        // Query accessing Customer and CustomerProfile
        Map<String, Object> query1 = new HashMap<>();
        query1.put("queryId", 1L);
        query1.put("executionCount", 3000L);
        query1.put("operationType", "SELECT");
        query1.put("tablesAccessed", Arrays.asList("Customer", "CustomerProfile"));
        queries.add(query1);
        
        // Query accessing just CustomerProfile
        Map<String, Object> query2 = new HashMap<>();
        query2.put("queryId", 2L);
        query2.put("executionCount", 2000L);
        query2.put("operationType", "SELECT");
        query2.put("tablesAccessed", Arrays.asList("CustomerProfile"));
        queries.add(query2);
        
        metrics.put("queries", queries);
        
        return metrics;
    }

    private Map<String, Object> createProductionMetricsWithCustomTableName() {
        Map<String, Object> metrics = new HashMap<>();
        
        Map<String, Object> qualifiedMetrics = new HashMap<>();
        Map<String, Object> tablePatterns = new HashMap<>();
        tablePatterns.put("frequentTableCombinations", new ArrayList<>());
        qualifiedMetrics.put("tableAccessPatterns", tablePatterns);
        metrics.put("qualifiedMetrics", qualifiedMetrics);
        
        // Query with custom table name
        List<Map<String, Object>> queries = new ArrayList<>();
        Map<String, Object> query = new HashMap<>();
        query.put("queryId", 1L);
        query.put("executionCount", 1500L);
        query.put("operationType", "SELECT");
        query.put("tablesAccessed", Arrays.asList("Customers")); // Matches entityWithCustomTable.tableName
        queries.add(query);
        
        metrics.put("queries", queries);
        
        return metrics;
    }

    private DenormalizationCandidate findCandidate(List<DenormalizationCandidate> candidates, String entityName) {
        return candidates.stream()
                .filter(c -> c.getPrimaryEntity().equals(entityName))
                .findFirst()
                .orElse(null);
    }
}