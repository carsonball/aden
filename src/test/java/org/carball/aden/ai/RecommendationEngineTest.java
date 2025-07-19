package org.carball.aden.ai;

import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.entity.NavigationProperty;
import org.carball.aden.model.entity.NavigationType;
import org.carball.aden.model.query.*;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.model.schema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RecommendationEngineTest {

    private RecommendationEngine engine;
    private DatabaseSchema testSchema;
    private List<QueryPattern> testQueryPatterns;

    @BeforeEach
    public void setUp() {
        engine = new RecommendationEngine("test-key");
        testSchema = createTestSchema();
        testQueryPatterns = createTestQueryPatterns();
    }

    @Test
    public void shouldSerializeTableSchemaWithAllDetails() throws Exception {
        // Use reflection to test private method
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeTableSchema", String.class, DatabaseSchema.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(engine, "Customer", testSchema);
        
        // Assert key information is present
        assertThat(result).contains("**Table:** Customer");
        assertThat(result).contains("**Primary Key:** Id (int)");
        assertThat(result).contains("**Columns:**");
        assertThat(result).contains("Id (int) NOT NULL");
        assertThat(result).contains("Email (nvarchar) NOT NULL");
        assertThat(result).contains("Status (nvarchar) NULL");
        assertThat(result).contains("FK -> Orders.CustomerId");
        assertThat(result).contains("**Indexes:**");
        assertThat(result).contains("IX_Customer_Email");
    }

    @Test
    public void shouldHandleTableNotFoundGracefully() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeTableSchema", String.class, DatabaseSchema.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(engine, "NonExistentTable", testSchema);
        
        assertThat(result).isEqualTo("Table not found in schema");
    }

    @Test
    public void shouldSerializeRelationshipsInBothDirections() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeRelationships", String.class, DatabaseSchema.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(engine, "Customer", testSchema);
        
        assertThat(result).contains("**Relationships:**");
        assertThat(result).contains("Customer.Id -> Orders.CustomerId");
        assertThat(result).contains("ONE_TO_MANY");
    }

    @Test
    public void shouldSerializeQueryPatternsWithDetails() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeQueryPatterns", String.class, List.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(engine, "Customer", testQueryPatterns);
        
        assertThat(result).contains("**Query Patterns:**");
        assertThat(result).contains("**WHERE_CLAUSE**");
        assertThat(result).contains("frequency: 5");
        assertThat(result).contains("WHERE columns: Status, Email");
        assertThat(result).contains("Filter conditions:");
        assertThat(result).contains("Status == [STRING]");
        assertThat(result).contains("Complex WHERE clause");
    }

    @Test
    public void shouldSerializeEntityFrameworkContext() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeEntityFrameworkContext", String.class, List.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(engine, "Customer", testQueryPatterns);
        
        assertThat(result).contains("**Entity Framework Context:**");
        assertThat(result).contains("**Navigation Properties");
        assertThat(result).contains("Customer.Orders");
        assertThat(result).contains("**Parameter Usage:**");
        assertThat(result).contains("customerId");
        assertThat(result).contains("**Complexity Indicators:**");
        assertThat(result).contains("Complex queries: 1");
    }

    @Test
    public void shouldHandleEmptyQueryPatterns() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeQueryPatterns", String.class, List.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(engine, "Customer", List.of());
        
        assertThat(result).contains("No query patterns found");
    }
    
    @Test
    public void shouldBuildEntityAnalysisSection() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("buildEntityAnalysis", 
                DenormalizationCandidate.class, AnalysisResult.class, DatabaseSchema.class, 
                List.class, Map.class);
        method.setAccessible(true);
        
        DenormalizationCandidate candidate = createTestCandidate();
        AnalysisResult analysis = createTestAnalysis();
        
        String result = (String) method.invoke(engine, candidate, analysis, testSchema, testQueryPatterns, null);
        
        assertThat(result).contains("**Primary Entity:** Customer");
        assertThat(result).contains("**Related Entities:** Orders");
        assertThat(result).contains("**Migration Complexity:** MEDIUM");
        assertThat(result).contains("**Usage Patterns:**");
        assertThat(result).contains("Eager Loading: 10 occurrences");
    }
    
    @Test
    public void shouldSerializeAllRelationships() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeAllRelationships", 
                List.class, DatabaseSchema.class);
        method.setAccessible(true);
        
        List<DenormalizationCandidate> candidates = Arrays.asList(
            createTestCandidate(),
            DenormalizationCandidate.builder()
                .primaryEntity("Order")
                .relatedEntities(Arrays.asList("Customer"))
                .complexity(MigrationComplexity.LOW)
                .reason("Test")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .score(50)
                .build()
        );
        
        // Add Order table to schema
        Table orderTable = new Table("Orders");
        orderTable.addColumn(Column.builder()
                .name("Id")
                .dataType("int")
                .primaryKey(true)
                .build());
        orderTable.addColumn(Column.builder()
                .name("CustomerId")
                .dataType("int")
                .foreignKey(true)
                .build());
        testSchema.addTable(orderTable);
        
        String result = (String) method.invoke(engine, candidates, testSchema);
        
        assertThat(result).contains("Customer");
        assertThat(result).contains("(ONE-MANY)");
        assertThat(result).contains("Orders");
        assertThat(result).contains("[Always loaded together]");
    }
    
    @Test
    public void shouldParseBatchRecommendationResponse() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("parseBatchRecommendationResponse", 
                String.class, List.class);
        method.setAccessible(true);
        
        String aiResponse = """
            ### Customer Recommendation:
            
            1. **Target Service**: DynamoDB
            2. **Table/Collection Design**: Single table design
            3. **Partition Key Strategy**: The partition key will be CustomerId
            
            ### Order Recommendation:
            
            1. **Target Service**: DynamoDB
            2. **Table/Collection Design**: Separate table
            3. **Partition Key Strategy**: The partition key will be OrderId
            """;
        
        List<DenormalizationCandidate> candidates = Arrays.asList(
            createTestCandidate(),
            DenormalizationCandidate.builder()
                .primaryEntity("Order")
                .relatedEntities(new ArrayList<>())
                .complexity(MigrationComplexity.LOW)
                .reason("Test")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .score(50)
                .build()
        );
        
        @SuppressWarnings("unchecked")
        List<NoSQLRecommendation> result = (List<NoSQLRecommendation>) method.invoke(engine, aiResponse, candidates);
        
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPrimaryEntity()).isEqualTo("Customer");
        assertThat(result.get(0).getTargetService()).isEqualTo(NoSQLTarget.DYNAMODB);
        assertThat(result.get(0).getPartitionKey().getAttribute()).isEqualTo("CustomerId");
        
        assertThat(result.get(1).getPrimaryEntity()).isEqualTo("Order");
        assertThat(result.get(1).getTargetService()).isEqualTo(NoSQLTarget.DYNAMODB);
        assertThat(result.get(1).getPartitionKey().getAttribute()).isEqualTo("OrderId");
    }

    @Test
    public void shouldIncludeSchemaInformationInBatchPrompt() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("buildBatchRecommendationPrompt", 
                AnalysisResult.class, DatabaseSchema.class, List.class, Map.class);
        method.setAccessible(true);
        
        AnalysisResult analysis = createTestAnalysisWithMultipleCandidates();
        
        String prompt = (String) method.invoke(engine, analysis, testSchema, testQueryPatterns, null);
        
        // Assert new batch prompt structure
        assertThat(prompt).contains("## Database Overview:");
        assertThat(prompt).contains("## Entity Relationships:");
        assertThat(prompt).contains("## Cross-Entity Access Patterns:");
        assertThat(prompt).contains("## Entity Analyses:");
        assertThat(prompt).contains("### Entity 1: Customer");
        assertThat(prompt).contains("### Entity 2: Order");
        assertThat(prompt).contains("## Requirements:");
        assertThat(prompt).contains("For EACH entity, provide:");
    }

    @Test
    public void shouldHandleNullSchemaGracefully() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("buildBatchRecommendationPrompt", 
                AnalysisResult.class, DatabaseSchema.class, List.class, Map.class);
        method.setAccessible(true);
        
        AnalysisResult analysis = createTestAnalysisWithMultipleCandidates();
        
        String prompt = (String) method.invoke(engine, analysis, null, null, null);
        
        // Should indicate schema not available
        assertThat(prompt).contains("Schema information not available");
        assertThat(prompt).contains("Production metrics not available");
        // But should still have other sections
        assertThat(prompt).contains("## Database Overview:");
        assertThat(prompt).contains("## Requirements:");
    }

    private DatabaseSchema createTestSchema() {
        DatabaseSchema schema = new DatabaseSchema();
        
        // Create Customer table
        Table customerTable = new Table("Customer");
        
        customerTable.addColumn(Column.builder()
                .name("Id")
                .dataType("int")
                .nullable(false)
                .primaryKey(true)
                .build());
        
        customerTable.addColumn(Column.builder()
                .name("Email")
                .dataType("nvarchar")
                .nullable(false)
                .build());
        
        customerTable.addColumn(Column.builder()
                .name("Status")
                .dataType("nvarchar")
                .nullable(true)
                .build());
        
        customerTable.addColumn(Column.builder()
                .name("OrderId")
                .dataType("int")
                .nullable(true)
                .foreignKey(true)
                .referencedTable("Orders")
                .referencedColumn("CustomerId")
                .build());
        
        customerTable.addIndex(Index.builder()
                .name("IX_Customer_Email")
                .columns(List.of("Email"))
                .unique(true)
                .build());
        
        schema.addTable(customerTable);
        
        // Add relationship
        schema.addRelationship(Relationship.builder()
                .name("FK_Customer_Orders")
                .fromTable("Customer")
                .fromColumn("Id")
                .toTable("Orders")
                .toColumn("CustomerId")
                .type(RelationshipType.ONE_TO_MANY)
                .build());
        
        return schema;
    }

    private List<QueryPattern> createTestQueryPatterns() {
        QueryPattern wherePattern = new QueryPattern("WHERE_CLAUSE", "Customer", 5, "CustomerService.cs");
        wherePattern.setQueryType(QueryType.WHERE_CLAUSE);
        wherePattern.addWhereClauseColumn("Status");
        wherePattern.addWhereClauseColumn("Email");
        wherePattern.setHasComplexWhere(true);
        
        FilterCondition statusCondition = FilterCondition.builder()
                .column("Status")
                .operator("==")
                .valueType("STRING")
                .frequency(3)
                .build();
        wherePattern.addFilterCondition(statusCondition);
        
        FilterCondition emailCondition = FilterCondition.builder()
                .column("Email")
                .operator("==")
                .valueType("PARAMETER")
                .frequency(2)
                .build();
        wherePattern.addFilterCondition(emailCondition);
        
        wherePattern.addParameterType("customerId");
        
        QueryPattern eagerLoadingPattern = new QueryPattern("EAGER_LOADING", "Customer.Orders", 3, "CustomerService.cs");
        eagerLoadingPattern.setQueryType(QueryType.EAGER_LOADING);
        
        return Arrays.asList(wherePattern, eagerLoadingPattern);
    }

    private DenormalizationCandidate createTestCandidate() {
        return DenormalizationCandidate.builder()
                .primaryEntity("Customer")
                .relatedEntities(List.of("Orders"))
                .complexity(MigrationComplexity.MEDIUM)
                .reason("High frequency eager loading")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .score(100)
                .build();
    }

    private AnalysisResult createTestAnalysis() {
        AnalysisResult analysis = new AnalysisResult();
        
        // Create a mock EntityModel for the EntityUsageProfile
        EntityModel entityModel = new EntityModel("Customer", "Customer.cs");
        EntityUsageProfile profile = new EntityUsageProfile(entityModel);
        profile.incrementEagerLoadingCount(10);
        profile.getAlwaysLoadedWithEntities().add("Orders");
        
        analysis.getUsageProfiles().put("Customer", profile);
        
        return analysis;
    }
    
    private AnalysisResult createTestAnalysisWithMultipleCandidates() {
        AnalysisResult analysis = new AnalysisResult();
        
        // Create Customer entity with navigation properties
        EntityModel customerEntity = new EntityModel("Customer", "Customer.cs");
        customerEntity.addNavigationProperty(new NavigationProperty(
                "Orders", "Order", NavigationType.ONE_TO_MANY));
        customerEntity.addNavigationProperty(new NavigationProperty(
                "Profile", "CustomerProfile", NavigationType.ONE_TO_ONE));
        
        EntityUsageProfile customerProfile = new EntityUsageProfile(customerEntity);
        customerProfile.incrementEagerLoadingCount(16);
        customerProfile.getAlwaysLoadedWithEntities().add("CustomerProfile");
        customerProfile.addRelatedEntity("Order", RelationshipType.ONE_TO_MANY);
        customerProfile.addRelatedEntity("CustomerProfile", RelationshipType.ONE_TO_ONE);
        
        // Create Order entity
        EntityModel orderEntity = new EntityModel("Order", "Order.cs");
        orderEntity.addNavigationProperty(new NavigationProperty(
                "Customer", "Customer", NavigationType.MANY_TO_ONE));
        
        EntityUsageProfile orderProfile = new EntityUsageProfile(orderEntity);
        orderProfile.incrementEagerLoadingCount(14);
        orderProfile.addRelatedEntity("Customer", RelationshipType.MANY_TO_ONE);
        
        analysis.getUsageProfiles().put("Customer", customerProfile);
        analysis.getUsageProfiles().put("Order", orderProfile);
        
        // Create denormalization candidates
        List<DenormalizationCandidate> candidates = Arrays.asList(
            DenormalizationCandidate.builder()
                .primaryEntity("Customer")
                .relatedEntities(Arrays.asList("CustomerProfile"))
                .complexity(MigrationComplexity.LOW)
                .reason("Read-heavy access pattern; Always loaded together")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .score(41)
                .build(),
            DenormalizationCandidate.builder()
                .primaryEntity("Order")
                .relatedEntities(new ArrayList<>())
                .complexity(MigrationComplexity.LOW)
                .reason("Read-heavy access pattern")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .score(39)
                .build()
        );
        
        analysis.setDenormalizationCandidates(candidates);
        analysis.setQueryPatterns(testQueryPatterns);
        
        return analysis;
    }
    
    @Test
    public void shouldSerializeProductionMetrics() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeProductionMetrics", 
                DenormalizationCandidate.class, Map.class);
        method.setAccessible(true);
        
        DenormalizationCandidate candidate = createTestCandidate();
        Map<String, Object> productionMetrics = createTestProductionMetrics();
        
        String result = (String) method.invoke(engine, candidate, productionMetrics);
        
        assertThat(result).contains("**Total Query Executions:** 1,042");
        assertThat(result).contains("**Operation Breakdown:**");
        assertThat(result).contains("UPDATE: 21 executions");
        assertThat(result).contains("SELECT: 1,021 executions");
        assertThat(result).contains("**Read/Write Ratio:** 48.6:1 (Read-heavy workload)");
        assertThat(result).contains("**Table Co-Access Patterns:**");
        assertThat(result).contains("Customer + CustomerProfile");
        assertThat(result).contains("98.0% of all queries");
    }
    
    @Test
    public void shouldSerializeCrossEntityPatterns() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("serializeCrossEntityPatterns", 
                List.class, Map.class);
        method.setAccessible(true);
        
        List<DenormalizationCandidate> candidates = Arrays.asList(createTestCandidate());
        Map<String, Object> productionMetrics = createTestProductionMetrics();
        
        String result = (String) method.invoke(engine, candidates, productionMetrics);
        
        assertThat(result).contains("Customer + CustomerProfile");
        assertThat(result).contains("1,021 queries");
        assertThat(result).contains("98.0% of all queries");
    }
    
    private Map<String, Object> createTestProductionMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        Map<String, Object> qualifiedMetrics = new HashMap<>();
        
        qualifiedMetrics.put("totalExecutions", 1042L);
        
        Map<String, Object> operations = new HashMap<>();
        operations.put("UPDATE", 21L);
        operations.put("SELECT", 1021L);
        qualifiedMetrics.put("operationBreakdown", operations);
        
        qualifiedMetrics.put("readWriteRatio", 48.6);
        qualifiedMetrics.put("isReadHeavy", true);
        
        Map<String, Object> tablePatterns = new HashMap<>();
        tablePatterns.put("hasStrongCoAccessPatterns", true);
        
        List<Map<String, Object>> combinations = new ArrayList<>();
        Map<String, Object> combo = new HashMap<>();
        combo.put("tables", Arrays.asList("Customer", "CustomerProfile"));
        combo.put("totalExecutions", 1021L);
        combo.put("executionPercentage", 98.0);
        combinations.add(combo);
        
        tablePatterns.put("frequentTableCombinations", combinations);
        qualifiedMetrics.put("tableAccessPatterns", tablePatterns);
        
        Map<String, Object> performance = new HashMap<>();
        performance.put("avgQueryDurationMs", 0.05);
        performance.put("avgLogicalReads", 3.0);
        qualifiedMetrics.put("performanceCharacteristics", performance);
        
        metrics.put("qualifiedMetrics", qualifiedMetrics);
        return metrics;
    }
}