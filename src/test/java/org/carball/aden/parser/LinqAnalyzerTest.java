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

public class LinqAnalyzerTest {

    private LinqAnalyzer analyzer;

    @BeforeEach
    public void setUp() {
        analyzer = new LinqAnalyzer();
    }

    @Test
    public void shouldDetectIncludePatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public Customer GetCustomerWithOrders(int id)
                {
                    return _context.Customers
                        .Include(c => c.Orders)
                        .FirstOrDefault(c => c.Id == id);
                }

                public List<Customer> GetAllCustomersWithProfiles()
                {
                    return _context.Customers
                        .Include(c => c.Profile)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        assertThat(patterns).isNotEmpty();

        // Should detect eager loading patterns
        assertThat(patterns).anyMatch(p ->
                p.getType().equals("EAGER_LOADING") &&
                        p.getTargetEntity().contains("Orders"));
    }

    @Test
    public void shouldDetectComplexIncludeChains(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class OrderService
            {
                private readonly ApplicationDbContext _context;

                public Order GetOrderDetails(int id)
                {
                    return _context.Orders
                        .Include(o => o.Customer)
                        .ThenInclude(c => c.Profile)
                        .Include(o => o.OrderItems)
                        .ThenInclude(oi => oi.Product)
                        .FirstOrDefault(o => o.Id == id);
                }
            }
            """;

        Files.writeString(tempDir.resolve("OrderService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        assertThat(patterns).anyMatch(p ->
                p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING);
    }
}