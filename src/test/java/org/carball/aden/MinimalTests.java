package org.carball.aden;

import org.carball.aden.analyzer.DotNetAnalyzer;
import org.carball.aden.analyzer.DotNetPatternAnalyzer;
import org.carball.aden.config.DotNetAnalyzerConfig;
import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.entity.NavigationType;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.parser.SchemaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MinimalTests {

    // SchemaParserTest - basic table parsing (COMPLETE)
    @Test
    public void shouldParseSimpleTable() {
        // Given
        String ddl = "CREATE TABLE Customer (Id int PRIMARY KEY, Name nvarchar(100))";
        SchemaParser parser = new SchemaParser();

        // When
        DatabaseSchema schema = parser.parseDDL(ddl);

        // Then
        assertThat(schema.getTables()).hasSize(1);
        assertThat(schema.getTables().get(0).getName()).isEqualTo("Customer");
        assertThat(schema.getTables().get(0).getColumns()).hasSize(2);
    }

    // Basic integration test (COMPLETE)
    @Test
    public void shouldAnalyzeSimpleSchema(@TempDir Path tempDir) throws Exception {
        // Given - Create test files
        String ddl = """
            CREATE TABLE Customer (
                Id int PRIMARY KEY,
                Name nvarchar(100)
            );
            CREATE TABLE Orders (
                Id int PRIMARY KEY,
                CustomerId int REFERENCES Customer(Id)
            );
            """;

        String customerEntity = """
            public class Customer {
                public int Id { get; set; }
                public string Name { get; set; }
                public virtual ICollection<Order> Orders { get; set; }
            }
            """;

        String orderEntity = """
            public class Order {
                public int Id { get; set; }
                public int CustomerId { get; set; }
                public virtual Customer Customer { get; set; }
            }
            """;

        String customerService = """
            public class CustomerService {
                private readonly AppDbContext _context;
            
                public Customer GetCustomerWithOrders(int id) {
                    return _context.Customers
                        .Include(c => c.Orders)
                        .FirstOrDefault(c => c.Id == id);
                }
            }
            """;

        // Write files
        Path schemaFile = tempDir.resolve("schema.sql");
        Files.writeString(schemaFile, ddl);

        Path modelsDir = tempDir.resolve("Models");
        Files.createDirectories(modelsDir);
        Files.writeString(modelsDir.resolve("Customer.cs"), customerEntity);
        Files.writeString(modelsDir.resolve("Order.cs"), orderEntity);

        Path servicesDir = tempDir.resolve("Services");
        Files.createDirectories(servicesDir);
        Files.writeString(servicesDir.resolve("CustomerService.cs"), customerService);

        // When - Run the analyzer
        DotNetAnalyzerConfig config = new DotNetAnalyzerConfig();
        config.setSchemaFile(schemaFile);
        config.setSourceDirectory(tempDir);
        config.setOpenAiApiKey("test-key"); // Won't actually call API

        DotNetAnalyzer analyzer = new DotNetAnalyzer(config);
        AnalysisResult result = analyzer.analyze();

        // Then - Verify components work together
        assertThat(result).isNotNull();
        assertThat(result.getUsageProfiles()).isNotEmpty();
        assertThat(result.getUsageProfiles()).containsKey("Customer");
        assertThat(result.getQueryPatterns()).isNotEmpty();

        // Should detect the Include pattern
        assertThat(result.getQueryPatterns()).anyMatch(p ->
                p.getTargetEntity().contains("Customer") &&
                        p.getType().contains("EAGER_LOADING")
        );
    }

    // Quick pattern analyzer test
    @Test
    public void shouldIdentifyDenormalizationCandidate() {
        // Given
        SchemaParser schemaParser = new SchemaParser();
        DatabaseSchema schema = schemaParser.parseDDL(
                "CREATE TABLE Customer (Id int PRIMARY KEY);"
        );

        EntityModel customer = new EntityModel("Customer", "Customer.cs");
        customer.addNavigationProperty(
                new org.carball.aden.model.entity.NavigationProperty(
                        "Orders", "Order", NavigationType.ONE_TO_MANY
                )
        );

        QueryPattern pattern = new QueryPattern(
                "EAGER_LOADING",
                "Customer.Orders",
                100, // High frequency
                "CustomerService.cs"
        );
        pattern.setQueryType(org.carball.aden.model.query.QueryType.EAGER_LOADING);

        DotNetPatternAnalyzer analyzer = new DotNetPatternAnalyzer();

        // When
        AnalysisResult result = analyzer.analyzePatterns(
                List.of(customer),
                List.of(pattern),
                schema,
                Map.of()
        );

        // Then
        assertThat(result.getDenormalizationCandidates()).isNotEmpty();
        assertThat(result.getDenormalizationCandidates().get(0).getPrimaryEntity())
                .isEqualTo("Customer");
    }
}