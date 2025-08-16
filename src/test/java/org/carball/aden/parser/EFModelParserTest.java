package org.carball.aden.parser;

import org.carball.aden.model.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EFModelParserTest {

    private EFModelParser parser;

    @BeforeEach
    public void setUp() {
        parser = new EFModelParser();
    }

    // 2. EFModelParserTest - navigation property detection (COMPLETE)
    @Test
    public void shouldFindNavigationProperty() {
        // Given
        String code = """
            public class Customer {
                public int Id { get; set; }
                public string Name { get; set; }
                public virtual ICollection<Order> Orders { get; set; }
            }
            """;
        EFModelParser parser = new EFModelParser();

        // When
        EntityModel entity = parser.parseEntityFromContent(code, "Customer.cs");

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getClassName()).isEqualTo("Customer");
        assertThat(entity.getNavigationProperties()).hasSize(1);
        assertThat(entity.getNavigationProperties().get(0).getPropertyName()).isEqualTo("Orders");
        assertThat(entity.getNavigationProperties().get(0).getTargetEntity()).isEqualTo("Order");
        assertThat(entity.getNavigationProperties().get(0).getType()).isEqualTo(NavigationType.ONE_TO_MANY);
    }

    @Test
    public void shouldIdentifyNavigationProperties() {
        String entityCode = """
            public class Customer
            {
                public int Id { get; set; }
                public string Name { get; set; }
                public virtual ICollection<Order> Orders { get; set; }
                public virtual CustomerProfile Profile { get; set; }
            }
            """;

        EntityModel entity = parser.parseEntityFromContent(entityCode, "Customer.cs");

        assertThat(entity).isNotNull();
        assertThat(entity.getClassName()).isEqualTo("Customer");
        assertThat(entity.getNavigationProperties()).hasSize(2);

        assertThat(entity.hasCollectionProperty("Orders")).isTrue();
        assertThat(entity.hasReferenceProperty("Profile")).isTrue();
    }

    @Test
    public void shouldParseEntityWithDataAnnotations() {
        String entityCode = """
            using System.ComponentModel.DataAnnotations;

            [Table("Customers")]
            public class Customer
            {
                [Key]
                public int Id { get; set; }

                [Required]
                [MaxLength(100)]
                public string Name { get; set; }

                [ForeignKey("AddressId")]
                public virtual Address Address { get; set; }

                public virtual ICollection<Order> Orders { get; set; }
            }
            """;

        EntityModel entity = parser.parseEntityFromContent(entityCode, "Customer.cs");

        assertThat(entity).isNotNull();
        assertThat(entity.getAnnotations()).isNotEmpty();
        assertThat(entity.getNavigationProperties()).hasSize(2);
    }

    @Test
    public void shouldParseEntitiesFromDirectory(@TempDir Path tempDir) throws Exception {
        // Create test entity files
        String customerEntity = """
            public class Customer
            {
                public int Id { get; set; }
                public string Name { get; set; }
                public virtual ICollection<Order> Orders { get; set; }
            }
            """;

        String orderEntity = """
            public class Order
            {
                public int Id { get; set; }
                public int CustomerId { get; set; }
                public virtual Customer Customer { get; set; }
                public virtual ICollection<OrderItem> OrderItems { get; set; }
            }
            """;

        Files.writeString(tempDir.resolve("Customer.cs"), customerEntity);
        Files.writeString(tempDir.resolve("Order.cs"), orderEntity);

        List<EntityModel> entities = parser.parseEntities(tempDir);

        assertThat(entities).hasSize(2);

        EntityModel customer = entities.stream()
                .filter(e -> e.getClassName().equals("Customer"))
                .findFirst()
                .orElse(null);

        assertThat(customer).isNotNull();
        assertThat(customer.getType()).isEqualTo(EntityType.AGGREGATE_ROOT);
    }

    @Test
    public void shouldParseDbContextWithDbSetMappings(@TempDir Path tempDir) throws Exception {
        // Given: DbContext file with various DbSet declarations
        String dbContextContent = """
            using System.Data.Entity;
            using TestApp.Models;
            
            namespace TestApp
            {
                public class AppDbContext : DbContext
                {
                    public DbSet<Customer> Customers { get; set; }
                    public DbSet<Order> Orders { get; set; }
                    public DbSet<OrderItem> OrderItems { get; set; }
                    public DbSet<Product> Products { get; set; }
                    public DbSet<CustomerProfile> CustomerProfiles { get; set; }
                }
            }
            """;

        Files.writeString(tempDir.resolve("AppDbContext.cs"), dbContextContent);

        // When: parsing entities which includes DbContext parsing
        parser.parseEntities(tempDir);
        Map<String, String> mapping = parser.getDbSetPropertyToEntityMap();

        // Then: should extract correct property-to-entity mappings
        assertThat(mapping).hasSize(5);
        assertThat(mapping.get("Customers")).isEqualTo("Customer");
        assertThat(mapping.get("Orders")).isEqualTo("Order");
        assertThat(mapping.get("OrderItems")).isEqualTo("OrderItem");
        assertThat(mapping.get("Products")).isEqualTo("Product");
        assertThat(mapping.get("CustomerProfiles")).isEqualTo("CustomerProfile");
    }

    @Test
    public void shouldHandleNonStandardDbSetPropertyNames(@TempDir Path tempDir) throws Exception {
        // Given: DbContext with non-conventional property names
        String dbContextContent = """
            using System.Data.Entity;
            using TestApp.Models;
            
            namespace TestApp
            {
                public class ApplicationDbContext : DbContext
                {
                    public DbSet<Customer> ClientRecords { get; set; }
                    public DbSet<Order> PurchaseHistory { get; set; }
                    public DbSet<Product> Inventory { get; set; }
                    public DbSet<User> SystemUsers { get; set; }
                }
            }
            """;

        Files.writeString(tempDir.resolve("ApplicationDbContext.cs"), dbContextContent);

        // When: parsing entities
        parser.parseEntities(tempDir);
        Map<String, String> mapping = parser.getDbSetPropertyToEntityMap();

        // Then: should handle non-standard property names correctly
        assertThat(mapping).hasSize(4);
        assertThat(mapping.get("ClientRecords")).isEqualTo("Customer");
        assertThat(mapping.get("PurchaseHistory")).isEqualTo("Order");
        assertThat(mapping.get("Inventory")).isEqualTo("Product");
        assertThat(mapping.get("SystemUsers")).isEqualTo("User");
    }

    @Test
    public void shouldParseMultipleDbContextFiles(@TempDir Path tempDir) throws Exception {
        // Given: Multiple DbContext files
        String dbContext1 = """
            using System.Data.Entity;
            
            public class OrderDbContext : DbContext
            {
                public DbSet<Order> Orders { get; set; }
                public DbSet<OrderItem> OrderItems { get; set; }
            }
            """;

        String dbContext2 = """
            using System.Data.Entity;
            
            public class CustomerDbContext : DbContext
            {
                public DbSet<Customer> Customers { get; set; }
                public DbSet<CustomerProfile> Profiles { get; set; }
            }
            """;

        Files.writeString(tempDir.resolve("OrderDbContext.cs"), dbContext1);
        Files.writeString(tempDir.resolve("CustomerDbContext.cs"), dbContext2);

        // When: parsing entities
        parser.parseEntities(tempDir);
        Map<String, String> mapping = parser.getDbSetPropertyToEntityMap();

        // Then: should combine mappings from all DbContext files
        assertThat(mapping).hasSize(4);
        assertThat(mapping.get("Orders")).isEqualTo("Order");
        assertThat(mapping.get("OrderItems")).isEqualTo("OrderItem");
        assertThat(mapping.get("Customers")).isEqualTo("Customer");
        assertThat(mapping.get("Profiles")).isEqualTo("CustomerProfile");
    }

    @Test
    public void shouldIgnoreNonDbContextFiles(@TempDir Path tempDir) throws Exception {
        // Given: Mix of entity files and DbContext file
        String customerEntity = """
            public class Customer
            {
                public int Id { get; set; }
                public string Name { get; set; }
                public virtual ICollection<Order> Orders { get; set; }
            }
            """;

        String dbContextContent = """
            using System.Data.Entity;
            
            public class AppDbContext : DbContext
            {
                public DbSet<Customer> Customers { get; set; }
            }
            """;

        String regularClass = """
            public class SomeService
            {
                public void DoSomething() { }
            }
            """;

        Files.writeString(tempDir.resolve("Customer.cs"), customerEntity);
        Files.writeString(tempDir.resolve("AppDbContext.cs"), dbContextContent);
        Files.writeString(tempDir.resolve("SomeService.cs"), regularClass);

        // When: parsing entities
        List<EntityModel> entities = parser.parseEntities(tempDir);
        Map<String, String> mapping = parser.getDbSetPropertyToEntityMap();

        // Then: should parse entities and DbContext mappings correctly
        assertThat(entities).hasSize(1); // Only Customer entity (has navigation properties)
        assertThat(entities.get(0).getClassName()).isEqualTo("Customer");
        assertThat(mapping).hasSize(1); // Only DbSet mapping
        assertThat(mapping.get("Customers")).isEqualTo("Customer");
    }

    @Test
    public void shouldHandleEmptyDbContext(@TempDir Path tempDir) throws Exception {
        // Given: DbContext with no DbSet properties
        String dbContextContent = """
            using System.Data.Entity;
            
            public class EmptyDbContext : DbContext
            {
                public EmptyDbContext() : base("connectionString") { }
            
                protected override void OnModelCreating(DbModelBuilder modelBuilder)
                {
                    base.OnModelCreating(modelBuilder);
                }
            }
            """;

        Files.writeString(tempDir.resolve("EmptyDbContext.cs"), dbContextContent);

        // When: parsing entities
        parser.parseEntities(tempDir);
        Map<String, String> mapping = parser.getDbSetPropertyToEntityMap();

        // Then: should return empty mapping
        assertThat(mapping).isEmpty();
    }
}
