package org.carball.aden.parser;

import org.carball.aden.model.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
