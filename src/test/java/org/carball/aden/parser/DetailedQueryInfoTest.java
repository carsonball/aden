package org.carball.aden.parser;

import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DetailedQueryInfoTest {

    private LinqAnalyzer analyzer;

    @BeforeEach
    public void setUp() {
        analyzer = new LinqAnalyzer();
    }

    @Test
    public void shouldExtractDetailedWhereClauseInformation(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class OrderService
            {
                private readonly ApplicationDbContext _context;

                public List<Order> GetComplexOrders(DateTime startDate, DateTime endDate, string status)
                {
                    return _context.Orders
                        .Where(o => (o.CreatedDate >= startDate && o.CreatedDate <= endDate) ||
                                   (o.Status == status && o.TotalAmount > 1000) ||
                                   o.Customer.VipStatus == true)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("OrderService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        
        // Show that detailed information IS being extracted
        System.out.println("WHERE clause columns detected: " + wherePattern.getWhereClauseColumns());
        System.out.println("Filter conditions detected: " + wherePattern.getFilterConditions().size());
        System.out.println("Parameter types detected: " + wherePattern.getParameterTypes());
        System.out.println("Complex WHERE flag: " + wherePattern.isHasComplexWhere());
        
        // The information is still there - the patterns are just consolidated
        assertThat(wherePattern.getWhereClauseColumns()).isNotEmpty();
        assertThat(wherePattern.getFilterConditions()).isNotEmpty();
        
        // Print detailed filter conditions
        wherePattern.getFilterConditions().forEach(fc -> System.out.println("Filter: " + fc.getColumn() + " " + fc.getOperator() + " " + fc.getValueType()));
    }

    @Test
    public void shouldExtractSpecificColumnReferences(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public Customer GetCustomerByEmailAndStatus(string email, string status)
                {
                    return _context.Customers
                        .Where(c => c.Email == email && c.Status == status)
                        .FirstOrDefault();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        
        // Detailed information IS extracted - just consolidated in tests
        System.out.println("Specific columns detected: " + wherePattern.getWhereClauseColumns());
        System.out.println("Number of filter conditions: " + wherePattern.getFilterConditions().size());
        
        // The RecommendationEngine will have access to this detailed information
        boolean hasEmailColumn = wherePattern.getWhereClauseColumns().contains("Email");
        boolean hasStatusColumn = wherePattern.getWhereClauseColumns().contains("Status");
        
        System.out.println("Has Email column: " + hasEmailColumn);
        System.out.println("Has Status column: " + hasStatusColumn);
        
        // This information is NOT lost - it's available for the RecommendationEngine
        assertThat(wherePattern.getWhereClauseColumns()).contains("Email", "Status");
        assertThat(wherePattern.getFilterConditions()).hasSize(2);
        
        // Verify specific filter conditions
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc ->
                fc.getColumn().equals("Email") && fc.getOperator().equals("=="));
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc ->
                fc.getColumn().equals("Status") && fc.getOperator().equals("=="));
    }
}