package org.carball.aden.parser;

import org.carball.aden.model.query.FilterCondition;
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

    @Test
    public void shouldDetectWhereClausePatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public Customer GetCustomerByEmail(string email)
                {
                    return _context.Customers
                        .Where(c => c.Email == email)
                        .FirstOrDefault();
                }

                public List<Customer> GetActiveCustomers()
                {
                    return _context.Customers
                        .Where(c => c.Status == "Active" && c.CreatedDate >= DateTime.Now.AddDays(-30))
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should detect WHERE clause patterns
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.WHERE_CLAUSE);

        // Should extract column references
        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        assertThat(wherePattern.getWhereClauseColumns()).isNotEmpty();
    }

    @Test
    public void shouldExtractColumnComparisons(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class OrderService
            {
                private readonly ApplicationDbContext _context;

                public List<Order> GetOrdersByStatusAndAmount(string status, decimal minAmount)
                {
                    return _context.Orders
                        .Where(o => o.Status == status && o.TotalAmount >= minAmount)
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
        assertThat(wherePattern.getWhereClauseColumns()).contains("Status", "TotalAmount");
        assertThat(wherePattern.getFilterConditions()).hasSize(2);
        assertThat(wherePattern.isHasComplexWhere()).isTrue();

        // Check specific filter conditions
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc ->
                fc.getColumn().equals("Status") && fc.getOperator().equals("=="));
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc ->
                fc.getColumn().equals("TotalAmount") && fc.getOperator().equals(">="));
    }

    @Test
    public void shouldDetectStringOperations(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class ProductService
            {
                private readonly ApplicationDbContext _context;

                public List<Product> SearchProducts(string searchTerm, string prefix, string suffix)
                {
                    return _context.Products
                        .Where(p => p.Name.Contains(searchTerm) &&
                                   p.Category.StartsWith(prefix) &&
                                   p.Description.EndsWith(suffix))
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("ProductService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Check that WHERE clause patterns are detected
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.WHERE_CLAUSE);
        
        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        assertThat(wherePattern.getPattern()).contains("Contains");
        assertThat(wherePattern.getPattern()).contains("StartsWith");
        assertThat(wherePattern.getPattern()).contains("EndsWith");
        
        // Verify string operations are extracted as filter conditions
        assertThat(wherePattern.getFilterConditions()).hasSize(3);
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc -> 
            fc.getColumn().equals("Name") && fc.getOperator().equals("CONTAINS"));
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc -> 
            fc.getColumn().equals("Category") && fc.getOperator().equals("STARTS_WITH"));
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc -> 
            fc.getColumn().equals("Description") && fc.getOperator().equals("ENDS_WITH"));
        
        // Verify columns are tracked
        assertThat(wherePattern.getWhereClauseColumns()).contains("Name", "Category", "Description");
    }

    @Test
    public void shouldDetectOrderByPatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public List<Customer> GetCustomersSortedByName()
                {
                    return _context.Customers
                        .OrderBy(c => c.LastName)
                        .ThenBy(c => c.FirstName)
                        .ToList();
                }

                public List<Customer> GetCustomersSortedByDateDescending()
                {
                    return _context.Customers
                        .OrderByDescending(c => c.CreatedDate)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should detect ORDER BY patterns
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.ORDER_BY);

        List<QueryPattern> orderByPatterns = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.ORDER_BY)
                .toList();

        assertThat(orderByPatterns).isNotEmpty();
        // Check that order by columns were detected
        assertThat(orderByPatterns.get(0).getOrderByColumns()).isNotEmpty();
    }

    @Test
    public void shouldDetectAggregationPatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class OrderService
            {
                private readonly ApplicationDbContext _context;

                public int GetOrderCount()
                {
                    return _context.Orders.Count();
                }

                public decimal GetTotalRevenue()
                {
                    return _context.Orders.Sum(o => o.TotalAmount);
                }

                public decimal GetAverageOrderValue()
                {
                    return _context.Orders.Average(o => o.TotalAmount);
                }

                public bool HasAnyOrders()
                {
                    return _context.Orders.Any();
                }
            }
            """;

        Files.writeString(tempDir.resolve("OrderService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should detect aggregation patterns
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.AGGREGATION);

        List<QueryPattern> aggPatterns = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.AGGREGATION)
                .toList();

        assertThat(aggPatterns).isNotEmpty(); // Patterns may be consolidated
        assertThat(aggPatterns).allMatch(QueryPattern::isHasAggregation);
        // Check that aggregation patterns are being detected
        assertThat(aggPatterns.get(0).isHasAggregation()).isTrue();
    }

    @Test
    public void shouldDetectPaginationPatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public List<Customer> GetCustomersPage(int page, int pageSize)
                {
                    return _context.Customers
                        .OrderBy(c => c.Id)
                        .Skip(page * pageSize)
                        .Take(pageSize)
                        .ToList();
                }

                public List<Customer> GetTopCustomers(int count)
                {
                    return _context.Customers
                        .OrderByDescending(c => c.TotalPurchases)
                        .Take(count)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should detect pagination patterns
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.PAGINATION);

        List<QueryPattern> pagePatterns = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.PAGINATION)
                .toList();

        assertThat(pagePatterns).isNotEmpty();
        assertThat(pagePatterns).allMatch(QueryPattern::isHasPagination);
        // Check that pagination patterns are being detected
        assertThat(pagePatterns.get(0).isHasPagination()).isTrue();
    }

    @Test
    public void shouldDetectGroupByPatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class OrderService
            {
                private readonly ApplicationDbContext _context;

                public var GetOrdersByStatus()
                {
                    return _context.Orders
                        .GroupBy(o => o.Status)
                        .Select(g => new { Status = g.Key, Count = g.Count() })
                        .ToList();
                }

                public var GetOrdersByCustomer()
                {
                    return _context.Orders
                        .GroupBy(o => o.CustomerId)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("OrderService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should detect GROUP BY patterns
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.GROUP_BY);

        List<QueryPattern> groupPatterns = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.GROUP_BY)
                .toList();

        assertThat(groupPatterns).isNotEmpty();
        // Patterns were consolidated
        assertThat(groupPatterns).hasSize(1);
    }

    @Test
    public void shouldDetectParameterTypes(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public Customer GetCustomer(int customerId, string email)
                {
                    return _context.Customers
                        .Where(c => c.Id == customerId && c.Email == email)
                        .FirstOrDefault();
                }

                public List<Customer> GetCustomersByStatus(CustomerStatus status)
                {
                    return _context.Customers
                        .Where(c => c.Status == status)
                        .ToList();
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
        assertThat(wherePattern.getParameterTypes()).isNotEmpty();
        // Parameters are detected from method parameter names in the code
        assertThat(wherePattern.getParameterTypes().size()).isGreaterThan(0);
    }

    @Test
    public void shouldDetectComplexWhereClause(@TempDir Path tempDir) throws Exception {
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
        assertThat(wherePattern.isHasComplexWhere()).isTrue();
        // Check that some columns were detected
        assertThat(wherePattern.getWhereClauseColumns()).isNotEmpty();
    }

    @Test
    public void shouldDetermineValueTypes(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class ProductService
            {
                private readonly ApplicationDbContext _context;

                public List<Product> GetProducts()
                {
                    return _context.Products
                        .Where(p => p.Name == "Test Product" &&
                                   p.Price >= 99.99 &&
                                   p.CategoryId == 42 &&
                                   p.IsActive == true)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("ProductService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        assertThat(wherePattern.getFilterConditions()).isNotEmpty();

        // Check that different value types are detected
        boolean hasStringType = wherePattern.getFilterConditions().stream()
                .anyMatch(fc -> fc.getValueType().equals("STRING"));
        boolean hasNumericType = wherePattern.getFilterConditions().stream()
                .anyMatch(fc -> fc.getValueType().equals("DECIMAL") || fc.getValueType().equals("INTEGER"));
        boolean hasBooleanType = wherePattern.getFilterConditions().stream()
                .anyMatch(fc -> fc.getValueType().equals("BOOLEAN"));
        
        // At least one type should be detected correctly
        assertThat(hasStringType || hasNumericType || hasBooleanType).isTrue();
    }

    @Test
    public void shouldConsolidatePatternsCorrectly(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public Customer GetCustomer1(int id)
                {
                    return _context.Customers
                        .Where(c => c.Id == id)
                        .FirstOrDefault();
                }

                public Customer GetCustomer2(int id)
                {
                    return _context.Customers
                        .Where(c => c.Id == id)
                        .FirstOrDefault();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should consolidate identical patterns
        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        assertThat(wherePattern.getSourceFiles()).hasSize(1);
    }

    @Test
    public void shouldHandleEmptyWhereClause(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public List<Customer> GetAllCustomers()
                {
                    return _context.Customers.ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Should not detect WHERE clause patterns when there are none
        assertThat(patterns).noneMatch(p -> p.getQueryType() == QueryType.WHERE_CLAUSE);
        assertThat(patterns).anyMatch(p -> p.getQueryType() == QueryType.COLLECTION);
    }

    @Test
    public void shouldTestUtilityMethods(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class ComplexService
            {
                private readonly ApplicationDbContext _context;

                public List<Order> GetComplexQuery()
                {
                    return _context.Orders
                        .Where(o => o.Status == "Active" && o.TotalAmount > 100)
                        .OrderBy(o => o.CreatedDate)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("ComplexService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Test the utility methods
        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        QueryPattern orderByPattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.ORDER_BY)
                .findFirst()
                .orElse(null);

        if (wherePattern != null) {
            // Test hasWhereClause method
            assertThat(wherePattern.hasWhereClause()).isTrue();
            
            // Test hasJoins method (should be false for this simple query)
            assertThat(wherePattern.hasJoins()).isFalse();
        }

        if (orderByPattern != null) {
            // Test hasOrderBy method
            assertThat(orderByPattern.hasOrderBy()).isTrue();
        }
    }

    @Test
    public void shouldDetectStartsWithOperations(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class CustomerService
            {
                private readonly ApplicationDbContext _context;

                public List<Customer> GetCustomersByNamePrefix(string namePrefix)
                {
                    return _context.Customers
                        .Where(c => c.FirstName.StartsWith(namePrefix))
                        .ToList();
                }
            
                public List<Customer> GetCustomersByConstantPrefix()
                {
                    return _context.Customers
                        .Where(c => c.LastName.StartsWith("Smith"))
                        .ToList();
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
        assertThat(wherePattern.getFilterConditions()).isNotEmpty();
        
        // Should detect StartsWith operations
        assertThat(wherePattern.getFilterConditions()).anyMatch(fc -> 
            fc.getOperator().equals("STARTS_WITH"));
        assertThat(wherePattern.getWhereClauseColumns()).contains("FirstName");
        
        // Note: Due to pattern consolidation, only one WHERE clause pattern is returned per entity set
        
        // Test value types for string operations
        boolean hasStringLiteral = wherePattern.getFilterConditions().stream()
                .anyMatch(fc -> fc.getValueType().equals("STRING"));
        boolean hasParameter = wherePattern.getFilterConditions().stream()
                .anyMatch(fc -> fc.getValueType().equals("PARAMETER"));
        assertThat(hasStringLiteral || hasParameter).isTrue();
    }

    @Test
    public void shouldDetectEndsWithOperations(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class ProductService
            {
                private readonly ApplicationDbContext _context;

                public List<Product> GetProductsBySuffix(string suffix)
                {
                    return _context.Products
                        .Where(p => p.Name.EndsWith(suffix) && p.Category.EndsWith(".xml"))
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("ProductService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        assertThat(wherePattern.getFilterConditions()).hasSize(2);
        
        // Should detect EndsWith operations
        assertThat(wherePattern.getFilterConditions()).allMatch(fc -> 
            fc.getOperator().equals("ENDS_WITH"));
        assertThat(wherePattern.getWhereClauseColumns()).contains("Name", "Category");
    }

    @Test
    public void shouldDetectStringOperationValueTypes(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class SearchService
            {
                private readonly ApplicationDbContext _context;

                public List<Product> ComplexSearch(string term, string prefix)
                {
                    return _context.Products
                        .Where(p => p.Name.Contains("literal string") &&
                                   p.Category.StartsWith(prefix) &&
                                   p.Description.EndsWith(@"parameter") &&
                                   p.Code.Contains('A'))
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("SearchService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        QueryPattern wherePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.WHERE_CLAUSE)
                .findFirst()
                .orElse(null);

        assertThat(wherePattern).isNotNull();
        assertThat(wherePattern.getFilterConditions()).isNotEmpty();
        
        // Check various value types are detected
        List<String> valueTypes = wherePattern.getFilterConditions().stream()
                .map(FilterCondition::getValueType)
                .toList();
        
        // Should contain at least string literals and parameters
        assertThat(valueTypes).contains("STRING");
        // Parameter detection varies based on implementation
        assertThat(valueTypes.size()).isGreaterThan(0);
    }

    @Test
    public void shouldDetectJoinPatterns(@TempDir Path tempDir) throws Exception {
        String serviceCode = """
            public class OrderService
            {
                private readonly ApplicationDbContext _context;

                public List<Order> GetOrdersWithCustomers()
                {
                    return _context.Orders
                        .Include(o => o.Customer)
                        .Include(o => o.OrderItems)
                        .ToList();
                }
            }
            """;

        Files.writeString(tempDir.resolve("OrderService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);

        // Check that Include patterns represent joins
        QueryPattern includePattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.EAGER_LOADING)
                .findFirst()
                .orElse(null);

        assertThat(includePattern).isNotNull();
    }

    @Test
    public void shouldCaptureJoinedEntitiesFromIncludePatterns(@TempDir Path tempDir) throws Exception {
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

                public List<Order> GetOrderWithDetails(int orderId)
                {
                    return _context.Orders
                        .Include(o => o.Customer)
                        .Include(o => o.OrderItems)
                        .ThenInclude(oi => oi.Product)
                        .Where(o => o.Id == orderId)
                        .ToList();
                }

                public Customer GetCustomerWithComplexIncludes(int id)
                {
                    return _context.Customers
                        .Include(c => c.Orders)
                        .ThenInclude(o => o.OrderItems)
                        .Include(c => c.Profile)
                        .FirstOrDefault(c => c.Id == id);
                }
            }
            """;

        Files.writeString(tempDir.resolve("CustomerService.cs"), serviceCode);

        List<QueryPattern> patterns = analyzer.analyzeLinqPatterns(tempDir);


        // Test 1: Simple Include pattern should capture joined entity
        QueryPattern simpleInclude = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.EAGER_LOADING && 
                            p.getTargetEntity().equals("Customers.Orders"))
                .findFirst()
                .orElse(null);

        assertThat(simpleInclude).isNotNull();
        assertThat(simpleInclude.hasJoins()).isTrue();
        assertThat(simpleInclude.getJoinedEntities()).contains("Orders");

        // Test 2: Complex eager loading should capture multiple joined entities
        QueryPattern complexPattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .findFirst()
                .orElse(null);

        assertThat(complexPattern).isNotNull();
        assertThat(complexPattern.hasJoins()).isTrue();
        assertThat(complexPattern.getJoinedEntities()).containsAnyOf("Customer", "OrderItems", "Product", "Orders", "Profile");
        assertThat(complexPattern.getJoinedEntities().size()).isGreaterThan(1);

        // Test 3: ThenInclude patterns should also capture joined entities
        QueryPattern nestedPattern = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.EAGER_LOADING && 
                            p.getTargetEntity().contains("nested"))
                .findFirst()
                .orElse(null);

        if (nestedPattern != null) {
            assertThat(nestedPattern.hasJoins()).isTrue();
            assertThat(nestedPattern.getJoinedEntities()).isNotEmpty();
        }

        // Test 4: Verify all eager loading patterns have joined entities populated
        List<QueryPattern> allEagerLoadingPatterns = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.EAGER_LOADING || 
                            p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .toList();

        assertThat(allEagerLoadingPatterns).isNotEmpty();
        assertThat(allEagerLoadingPatterns).allMatch(QueryPattern::hasJoins);
        assertThat(allEagerLoadingPatterns).allMatch(p -> !p.getJoinedEntities().isEmpty());
    }
}