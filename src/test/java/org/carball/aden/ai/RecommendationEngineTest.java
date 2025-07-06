package org.carball.aden.ai;

import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.query.*;
import org.carball.aden.model.schema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

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
        
        String result = (String) method.invoke(engine, "Customer", Arrays.asList());
        
        assertThat(result).contains("No query patterns found");
    }

    @Test
    public void shouldIncludeSchemaInformationInPrompt() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("buildRecommendationPrompt", 
                DenormalizationCandidate.class, AnalysisResult.class, DatabaseSchema.class, List.class);
        method.setAccessible(true);
        
        DenormalizationCandidate candidate = createTestCandidate();
        AnalysisResult analysis = createTestAnalysis();
        
        String prompt = (String) method.invoke(engine, candidate, analysis, testSchema, testQueryPatterns);
        
        // Assert schema sections are included
        assertThat(prompt).contains("## Database Schema Context:");
        assertThat(prompt).contains("**Table:** Customer");
        assertThat(prompt).contains("## Query Pattern Analysis:");
        assertThat(prompt).contains("**Query Patterns:");
        assertThat(prompt).contains("## Requirements:");
    }

    @Test
    public void shouldFallbackWhenSchemaIsNull() throws Exception {
        Method method = RecommendationEngine.class.getDeclaredMethod("buildRecommendationPrompt", 
                DenormalizationCandidate.class, AnalysisResult.class, DatabaseSchema.class, List.class);
        method.setAccessible(true);
        
        DenormalizationCandidate candidate = createTestCandidate();
        AnalysisResult analysis = createTestAnalysis();
        
        String prompt = (String) method.invoke(engine, candidate, analysis, null, null);
        
        // Should not contain schema sections
        assertThat(prompt).doesNotContain("## Database Schema Context:");
        assertThat(prompt).doesNotContain("## Query Pattern Analysis:");
        // But should still have basic sections
        assertThat(prompt).contains("## Entity Analysis:");
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
                .columns(Arrays.asList("Email"))
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
                .relatedEntities(Arrays.asList("Orders"))
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
        profile.getAlwaysLoadedWithEntities().addAll(Arrays.asList("Orders"));
        
        analysis.getUsageProfiles().put("Customer", profile);
        
        return analysis;
    }
}