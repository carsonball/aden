package org.carball.aden.analyzer;

import org.carball.aden.config.MigrationThresholds;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.*;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.DatabaseSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PatternAnalysisTest {

    private DotNetPatternAnalyzer analyzer;

    @BeforeEach
    public void setUp() {
        MigrationThresholds thresholds = MigrationThresholds.defaults();
        analyzer = new DotNetPatternAnalyzer(thresholds);
    }

    @Test
    public void shouldIdentifyDenormalizationCandidate() {
        // Given: Customer entity with Orders navigation property
        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        customer.addNavigationProperty(new NavigationProperty("Orders", "Order", NavigationType.ONE_TO_MANY));

        // And: Frequent LINQ queries with .Include(c => c.Orders)
        QueryPattern pattern = new QueryPattern("EAGER_LOADING", "Customer.Orders", 150, "CustomerService.cs");
        pattern.setQueryType(QueryType.EAGER_LOADING);

        // When: analyzing patterns
        DatabaseSchema mockSchema = new DatabaseSchema();
        AnalysisResult result = analyzer.analyzePatterns(
                Arrays.asList(customer),
                Arrays.asList(pattern),
                mockSchema,
                new HashMap<>()
        );

        // Then: should recommend denormalization
        assertThat(result.getDenormalizationCandidates()).hasSize(1);

        DenormalizationCandidate candidate = result.getDenormalizationCandidates().get(0);
        assertThat(candidate.getPrimaryEntity()).isEqualTo("Customer");
        assertThat(candidate.getRecommendedTarget()).isEqualTo(NoSQLTarget.DYNAMODB);
    }

    @Test
    public void shouldCalculateComplexityCorrectly() {
        // Given: Entity with multiple navigation properties
        EntityModel order = new EntityModel("Order", "Order.cs");
        order.addNavigationProperty(new NavigationProperty("Customer", "Customer", NavigationType.MANY_TO_ONE));
        order.addNavigationProperty(new NavigationProperty("OrderItems", "OrderItem", NavigationType.ONE_TO_MANY));
        order.addNavigationProperty(new NavigationProperty("Payment", "Payment", NavigationType.ONE_TO_ONE));

        // And: Complex query patterns
        List<QueryPattern> patterns = Arrays.asList(
                new QueryPattern("COMPLEX_EAGER_LOADING", "Order", 50, "OrderService.cs"),
                new QueryPattern("EAGER_LOADING", "Order.OrderItems", 100, "OrderService.cs")
        );
        patterns.forEach(p -> p.setQueryType(QueryType.EAGER_LOADING));

        // When: analyzing
        DatabaseSchema mockSchema = new DatabaseSchema();
        AnalysisResult result = analyzer.analyzePatterns(
                Arrays.asList(order),
                patterns,
                mockSchema,
                new HashMap<>()
        );

        // Then: should have appropriate complexity
        assertThat(result.getDenormalizationCandidates()).isNotEmpty();
        DenormalizationCandidate candidate = result.getDenormalizationCandidates().get(0);
        assertThat(candidate.getComplexity()).isEqualTo(MigrationComplexity.LOW);
    }

    @Test
    public void shouldUseDbSetMappingToMatchQueryPatternsWithEntities() {
        // Given: Entity with specific name
        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        customer.getNavigationProperties().add(
                new NavigationProperty("Orders", "Order", NavigationType.ONE_TO_MANY)
        );

        // Given: Query pattern using DbContext property name (plural)
        QueryPattern pattern = new QueryPattern(
                "EAGER_LOADING",
                "Customers",  // DbContext property name (plural)
                51,  // High frequency to exceed threshold
                "CustomerService.cs"
        );
        pattern.setQueryType(QueryType.EAGER_LOADING);

        // Given: DbSet mapping that maps property to entity
        Map<String, String> dbSetMapping = new HashMap<>();
        dbSetMapping.put("Customers", "Customer");  // Property -> Entity mapping

        // When: analyzing patterns with DbSet mapping
        DatabaseSchema mockSchema = new DatabaseSchema();
        AnalysisResult result = analyzer.analyzePatterns(
                Arrays.asList(customer),
                Arrays.asList(pattern),
                mockSchema,
                dbSetMapping
        );

        // Then: should match query pattern to entity correctly
        assertThat(result.getDenormalizationCandidates()).hasSize(1);
        DenormalizationCandidate candidate = result.getDenormalizationCandidates().get(0);
        assertThat(candidate.getPrimaryEntity()).isEqualTo("Customer");
    }

    @Test
    public void shouldHandleMismatchedDbSetMappings() {
        // Given: Entity with one name
        EntityModel order = new EntityModel("Order", "Order.cs");
        order.getNavigationProperties().add(
                new NavigationProperty("OrderItems", "OrderItem", NavigationType.ONE_TO_MANY)
        );

        // Given: Query pattern using non-standard DbContext property name
        QueryPattern pattern = new QueryPattern(
                "EAGER_LOADING",
                "PurchaseHistory",  // Non-standard property name
                51,  // High frequency to exceed threshold
                "OrderService.cs"
        );
        pattern.setQueryType(QueryType.EAGER_LOADING);

        // Given: DbSet mapping with non-standard property names
        Map<String, String> dbSetMapping = new HashMap<>();
        dbSetMapping.put("PurchaseHistory", "Order");  // Non-standard mapping

        // When: analyzing patterns
        DatabaseSchema mockSchema = new DatabaseSchema();
        AnalysisResult result = analyzer.analyzePatterns(
                Arrays.asList(order),
                Arrays.asList(pattern),
                mockSchema,
                dbSetMapping
        );

        // Then: should still match correctly using the mapping
        assertThat(result.getDenormalizationCandidates()).hasSize(1);
        DenormalizationCandidate candidate = result.getDenormalizationCandidates().get(0);
        assertThat(candidate.getPrimaryEntity()).isEqualTo("Order");
    }

    @Test
    public void shouldHandleQueryPatternsWithoutDbSetMapping() {
        // Given: Entity
        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        customer.getNavigationProperties().add(
                new NavigationProperty("Orders", "Order", NavigationType.ONE_TO_MANY)
        );

        // Given: Query pattern with entity name directly (no DbContext property)
        QueryPattern pattern = new QueryPattern(
                "EAGER_LOADING",
                "Customer",  // Direct entity name
                51,  // High frequency to exceed threshold
                "CustomerService.cs"
        );
        pattern.setQueryType(QueryType.EAGER_LOADING);

        // Given: Empty DbSet mapping
        Map<String, String> dbSetMapping = new HashMap<>();

        // When: analyzing patterns
        DatabaseSchema mockSchema = new DatabaseSchema();
        AnalysisResult result = analyzer.analyzePatterns(
                Arrays.asList(customer),
                Arrays.asList(pattern),
                mockSchema,
                dbSetMapping
        );

        // Then: should fall back to direct matching
        assertThat(result.getDenormalizationCandidates()).hasSize(1);
        DenormalizationCandidate candidate = result.getDenormalizationCandidates().get(0);
        assertThat(candidate.getPrimaryEntity()).isEqualTo("Customer");
    }
}