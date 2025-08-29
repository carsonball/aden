package org.carball.aden.analyzer;

import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.analysis.DenormalizationCandidate;
import org.carball.aden.model.analysis.EntityUsageProfile;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.query.*;
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
        analyzer = new DotNetPatternAnalyzer();

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
        QueryPattern pattern1 = new QueryPattern("SingleEntity", "Customer", "Repository.cs");
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
        QueryStoreAnalysis productionMetrics = createProductionMetrics();

        // Analyze without production metrics first
        AnalysisResult resultWithoutMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping);
        
        // Analyze with production metrics
        AnalysisResult resultWithMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Without production metrics, CustomerProfile should not be a candidate
        assertThat(resultWithoutMetrics.denormalizationCandidates())
                .extracting(DenormalizationCandidate::getPrimaryEntity)
                .doesNotContain("CustomerProfile");

        // With production metrics, CustomerProfile should be a candidate
        assertThat(resultWithMetrics.denormalizationCandidates())
                .extracting(DenormalizationCandidate::getPrimaryEntity)
                .contains("CustomerProfile");

        // The candidate should include Customer as a related entity due to co-access
        DenormalizationCandidate profileCandidate = resultWithMetrics.denormalizationCandidates()
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
        QueryPattern pattern2 = new QueryPattern("EagerLoading", "Customer.Orders", "Repository.cs");
        pattern2.setQueryType(QueryType.EAGER_LOADING);
        queryPatterns.add(pattern2);

        QueryStoreAnalysis productionMetrics = createProductionMetrics();

        AnalysisResult resultWithoutMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping);
        
        AnalysisResult resultWithMetrics = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Find Customer candidate in both results
        DenormalizationCandidate customerWithoutMetrics = findCandidate(
                resultWithoutMetrics.denormalizationCandidates(), "Customer");
        DenormalizationCandidate customerWithMetrics = findCandidate(
                resultWithMetrics.denormalizationCandidates(), "Customer");

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

        QueryStoreAnalysis productionMetrics = createProductionMetricsWithCustomTableName();

        AnalysisResult result = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Check that usage profiles have production data
        EntityUsageProfile profile = result.usageProfiles().get("CustomerEntity");
        assertThat(profile).isNotNull();
        // The production execution count comes from co-access patterns, not individual queries
        // But we should have production read/write ratio from the query analysis
        assertThat(profile.getProductionReadWriteRatio()).isEqualTo(1500.0); // All reads, no writes
    }

    private QueryStoreAnalysis createProductionMetrics() {
        // Individual queries
        List<AnalyzedQuery> queries = new ArrayList<>();
        
        // Query accessing Customer and CustomerProfile
        AnalyzedQuery query1 = AnalyzedQuery.builder()
            .queryId(1L)
            .executionCount(3000L)
            .operationType("SELECT")
            .tablesAccessed(Arrays.asList("Customer", "CustomerProfile"))
            .build();
        queries.add(query1);
        
        // Query accessing just CustomerProfile
        AnalyzedQuery query2 = AnalyzedQuery.builder()
            .queryId(2L)
            .executionCount(2000L)
            .operationType("SELECT")
            .tablesAccessed(List.of("CustomerProfile"))
            .build();
        queries.add(query2);
        
        // Query store metrics
        QueryStoreMetrics queryStoreMetrics = new QueryStoreMetrics();
        queryStoreMetrics.setTotalExecutions(5000L);

        List<TableCombination> frequentCombinations = new ArrayList<>();
        
        // Customer and CustomerProfile are frequently accessed together
        TableCombination combo1 = new TableCombination();
        combo1.setTables(Arrays.asList("Customer", "CustomerProfile"));
        combo1.setTotalExecutions(5000L);
        combo1.setExecutionPercentage(45.0);
        frequentCombinations.add(combo1);

        // Table access patterns
        TableAccessPatterns tablePatterns = new TableAccessPatterns(frequentCombinations, true);
        queryStoreMetrics.setTableAccessPatterns(tablePatterns);

        return new QueryStoreAnalysis("TestDB", "QUERY_STORE_PRODUCTION_METRICS", new Date(),
                2, queries, queryStoreMetrics);
    }

    private QueryStoreAnalysis createProductionMetricsWithCustomTableName() {
        // Query with custom table name
        List<AnalyzedQuery> queries = new ArrayList<>();
        AnalyzedQuery query = AnalyzedQuery.builder()
            .queryId(1L)
            .executionCount(1500L)
            .operationType("SELECT")
            .tablesAccessed(List.of("Customers")) // Matches entityWithCustomTable.tableName
            .build();
        queries.add(query);
        
        // Query store metrics
        QueryStoreMetrics queryStoreMetrics = new QueryStoreMetrics();
        queryStoreMetrics.setTotalExecutions(1500L);
        
        TableAccessPatterns tablePatterns = new TableAccessPatterns(new ArrayList<>(), false);
        queryStoreMetrics.setTableAccessPatterns(tablePatterns);
        
        return new QueryStoreAnalysis("TestDB", "QUERY_STORE_PRODUCTION_METRICS", new Date(),
                1, queries, queryStoreMetrics);
    }

    @Test
    void shouldNotOverCountExecutionsInTableCombinations() {
        // Create production metrics with a single table combination
        List<AnalyzedQuery> queries = new ArrayList<>();
        
        QueryStoreMetrics queryStoreMetrics = new QueryStoreMetrics();
        queryStoreMetrics.setTotalExecutions(1000L);

        List<TableCombination> frequentCombinations = new ArrayList<>();
        
        // Single combination with 3 tables and 500 executions
        TableCombination combo = new TableCombination();
        combo.setTables(Arrays.asList("Customer", "Order", "CustomerProfile"));
        combo.setTotalExecutions(500L);
        combo.setExecutionPercentage(50.0);
        frequentCombinations.add(combo);

        TableAccessPatterns tablePatterns = new TableAccessPatterns(frequentCombinations, true);
        queryStoreMetrics.setTableAccessPatterns(tablePatterns);

        QueryStoreAnalysis productionMetrics = new QueryStoreAnalysis(
                "TestDB", "QUERY_STORE_TEST", new Date(), 1, queries, queryStoreMetrics);

        // Analyze patterns with production metrics
        AnalysisResult result = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Each entity should have exactly 500 production executions (not 1500 or 1000)
        EntityUsageProfile customerProfile = result.usageProfiles().get("Customer");
        EntityUsageProfile orderProfile = result.usageProfiles().get("Order");
        EntityUsageProfile profileProfile = result.usageProfiles().get("CustomerProfile");

        assertThat(customerProfile.getProductionExecutionCount()).isEqualTo(500L);
        assertThat(orderProfile.getProductionExecutionCount()).isEqualTo(500L);
        assertThat(profileProfile.getProductionExecutionCount()).isEqualTo(500L);

        // Each entity should have the other two as co-accessed entities with 500 executions each
        assertThat(customerProfile.getCoAccessedEntities())
                .containsEntry("Order", 500)
                .containsEntry("CustomerProfile", 500);
        
        assertThat(orderProfile.getCoAccessedEntities())
                .containsEntry("Customer", 500)
                .containsEntry("CustomerProfile", 500);
        
        assertThat(profileProfile.getCoAccessedEntities())
                .containsEntry("Customer", 500)
                .containsEntry("Order", 500);
    }

    @Test
    void shouldHandleMultipleTableCombinationsCorrectly() {
        // Create production metrics with two separate table combinations
        List<AnalyzedQuery> queries = new ArrayList<>();
        
        QueryStoreMetrics queryStoreMetrics = new QueryStoreMetrics();
        queryStoreMetrics.setTotalExecutions(1500L);

        List<TableCombination> frequentCombinations = new ArrayList<>();
        
        // First combination: Customer + Order (300 executions)
        TableCombination combo1 = new TableCombination();
        combo1.setTables(Arrays.asList("Customer", "Order"));
        combo1.setTotalExecutions(300L);
        combo1.setExecutionPercentage(20.0);
        frequentCombinations.add(combo1);
        
        // Second combination: Customer + CustomerProfile (700 executions)
        TableCombination combo2 = new TableCombination();
        combo2.setTables(Arrays.asList("Customer", "CustomerProfile"));
        combo2.setTotalExecutions(700L);
        combo2.setExecutionPercentage(46.7);
        frequentCombinations.add(combo2);

        TableAccessPatterns tablePatterns = new TableAccessPatterns(frequentCombinations, true);
        queryStoreMetrics.setTableAccessPatterns(tablePatterns);

        QueryStoreAnalysis productionMetrics = new QueryStoreAnalysis(
                "TestDB", "QUERY_STORE_TEST", new Date(), 1, queries, queryStoreMetrics);

        // Analyze patterns with production metrics
        AnalysisResult result = analyzer.analyzePatterns(
                entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        // Customer appears in both combinations, so should have 300 + 700 = 1000 executions
        EntityUsageProfile customerProfile = result.usageProfiles().get("Customer");
        assertThat(customerProfile.getProductionExecutionCount()).isEqualTo(1000L);
        
        // Order appears in only first combination, so should have 300 executions
        EntityUsageProfile orderProfile = result.usageProfiles().get("Order");
        assertThat(orderProfile.getProductionExecutionCount()).isEqualTo(300L);
        
        // CustomerProfile appears in only second combination, so should have 700 executions
        EntityUsageProfile profileProfile = result.usageProfiles().get("CustomerProfile");
        assertThat(profileProfile.getProductionExecutionCount()).isEqualTo(700L);

        // Check co-access relationships
        assertThat(customerProfile.getCoAccessedEntities())
                .containsEntry("Order", 300)
                .containsEntry("CustomerProfile", 700);
        
        assertThat(orderProfile.getCoAccessedEntities())
                .containsEntry("Customer", 300);
        
        assertThat(profileProfile.getCoAccessedEntities())
                .containsEntry("Customer", 700);
    }

    private DenormalizationCandidate findCandidate(List<DenormalizationCandidate> candidates, String entityName) {
        return candidates.stream()
                .filter(c -> c.getPrimaryEntity().equals(entityName))
                .findFirst()
                .orElse(null);
    }
}