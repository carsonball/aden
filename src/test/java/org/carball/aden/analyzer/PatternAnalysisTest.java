package org.carball.aden.analyzer;

import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.*;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.DatabaseSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PatternAnalysisTest {

    private DotNetPatternAnalyzer analyzer;

    @BeforeEach
    public void setUp() {
        analyzer = new DotNetPatternAnalyzer();
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
                mockSchema
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
                mockSchema
        );

        // Then: should have appropriate complexity
        assertThat(result.getDenormalizationCandidates()).isNotEmpty();
        DenormalizationCandidate candidate = result.getDenormalizationCandidates().get(0);
        assertThat(candidate.getComplexity()).isEqualTo(MigrationComplexity.LOW);
    }
}