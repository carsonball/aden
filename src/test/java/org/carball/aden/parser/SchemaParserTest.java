package org.carball.aden.parser;

import org.carball.aden.model.schema.Column;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.model.schema.Relationship;
import org.carball.aden.model.schema.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SchemaParserTest {

    private SchemaParser parser;

    @BeforeEach
    public void setUp() {
        parser = new SchemaParser();
    }

    @Test
    public void shouldParseSimpleTable() {
        String ddl = "CREATE TABLE Customer (Id int PRIMARY KEY, Name nvarchar(100))";
        DatabaseSchema schema = parser.parseDDL(ddl);
        assertThat(schema.getTables()).hasSize(1);
    }

    @Test
    public void shouldParseBasicCreateTable() {
        String ddl = """
            CREATE TABLE Customer (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL,
                Email nvarchar(100) UNIQUE
            )
            """;

        DatabaseSchema schema = parser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(1);

        Table table = schema.getTables().get(0);
        assertThat(table.getName()).isEqualTo("Customer");
        assertThat(table.getColumns()).hasSize(3);

        Column idColumn = table.getColumns().stream()
                .filter(c -> c.getName().equals("Id"))
                .findFirst()
                .orElse(null);
        assertThat(idColumn).isNotNull();
        assertThat(idColumn.getDataType()).isEqualTo("int");
        assertThat(idColumn.isPrimaryKey()).isTrue();

        Column nameColumn = table.getColumns().stream()
                .filter(c -> c.getName().equals("Name"))
                .findFirst()
                .orElse(null);
        assertThat(nameColumn).isNotNull();
        assertThat(nameColumn.isNullable()).isFalse();
    }

    @Test
    public void shouldExtractForeignKeyRelationships() {
        String ddl = """
            CREATE TABLE Customer (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL
            );

            CREATE TABLE Orders (
                Id int PRIMARY KEY,
                CustomerId int REFERENCES Customer(Id),
                OrderDate datetime
            );
            """;

        DatabaseSchema schema = parser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(2);
        assertThat(schema.getRelationships()).hasSize(1);

        Relationship relationship = schema.getRelationships().get(0);
        assertThat(relationship.getFromTable()).isEqualTo("Orders");
        assertThat(relationship.getFromColumn()).isEqualTo("CustomerId");
        assertThat(relationship.getToTable()).isEqualTo("Customer");
        assertThat(relationship.getToColumn()).isEqualTo("Id");
    }

    @Test
    public void shouldHandleSqlServerBracketSyntax() {
        String ddl = """
            CREATE TABLE [dbo].[Customer] (
                [Id] [int] IDENTITY(1,1) NOT NULL,
                [FirstName] [nvarchar](50) NOT NULL,
                [LastName] [nvarchar](50) NOT NULL,
                [CreatedDate] [datetime] DEFAULT (GETDATE()),
                PRIMARY KEY ([Id])
            )
            """;

        DatabaseSchema schema = parser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(1);

        Table table = schema.getTables().get(0);
        assertThat(table.getName()).isEqualTo("Customer");
        assertThat(table.getColumns()).hasSize(4);

        Column idColumn = table.getColumns().stream()
                .filter(c -> c.getName().equals("Id"))
                .findFirst()
                .orElse(null);
        assertThat(idColumn).isNotNull();
        assertThat(idColumn.isPrimaryKey()).isTrue();
    }

    @Test
    public void shouldHandleBracketedTableNameOrder() {
        String ddl = """
            CREATE TABLE [Order] (
                Id int IDENTITY(1,1) PRIMARY KEY,
                CustomerId int NOT NULL,
                OrderDate datetime NOT NULL,
                TotalAmount decimal(10,2) NOT NULL,
                CONSTRAINT FK_Order_Customer FOREIGN KEY (CustomerId) 
                    REFERENCES Customer(Id)
            );
            """;

        DatabaseSchema schema = parser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(1);
        
        Table table = schema.getTables().get(0);
        assertThat(table.getName()).isEqualTo("Order");
        assertThat(table.getColumns()).hasSize(4);

        Column idColumn = table.getColumns().stream()
                .filter(c -> c.getName().equals("Id"))
                .findFirst()
                .orElse(null);
        assertThat(idColumn).isNotNull();
        assertThat(idColumn.isPrimaryKey()).isTrue();
    }

    @Test
    public void shouldHandleTableLevelForeignKeyConstraints() {
        String ddl = """
            CREATE TABLE OrderItem (
                OrderId int NOT NULL,
                ProductId int NOT NULL,
                Quantity int NOT NULL,
                PRIMARY KEY (OrderId, ProductId),
                CONSTRAINT FK_OrderItem_Order FOREIGN KEY (OrderId) REFERENCES Orders(Id),
                CONSTRAINT FK_OrderItem_Product FOREIGN KEY (ProductId) REFERENCES Product(Id)
            );
            """;

        DatabaseSchema schema = parser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(1);
        assertThat(schema.getRelationships()).hasSize(2);

        // Should have composite primary key as index
        Table table = schema.getTables().get(0);
        assertThat(table.getIndexes()).anyMatch(idx ->
                idx.getName().startsWith("PK_") &&
                        idx.getColumns().containsAll(List.of("OrderId", "ProductId")));
    }

    @Test
    public void shouldParseFromFile(@TempDir Path tempDir) throws Exception {
        String ddl = """
            CREATE TABLE Product (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL,
                CategoryId int REFERENCES Category(Id)
            );

            CREATE TABLE Category (
                Id int PRIMARY KEY,
                Name nvarchar(50) NOT NULL
            );
            """;

        Path schemaFile = tempDir.resolve("schema.sql");
        Files.writeString(schemaFile, ddl);

        DatabaseSchema schema = parser.parseDDL(schemaFile);

        assertThat(schema.getTables()).hasSize(2);
        assertThat(schema.getRelationships()).hasSize(1);
    }

    @Test
    public void shouldHandleInvalidSql() {
        String invalidDdl = """
            CREATE TABLE Invalid
                This is not valid SQL
            );
            """;

        assertThatThrownBy(() -> parser.parseDDL(invalidDdl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid SQL DDL");
    }

    @Test
    public void shouldParseComplexRealWorldSchema() {
        String ddl = """
            CREATE TABLE [dbo].[AspNetUsers] (
                [Id] [nvarchar](450) NOT NULL,
                [UserName] [nvarchar](256) NULL,
                [NormalizedUserName] [nvarchar](256) NULL,
                [Email] [nvarchar](256) NULL,
                [EmailConfirmed] [bit] NOT NULL,
                [PasswordHash] [nvarchar](max) NULL,
                [SecurityStamp] [nvarchar](max) NULL,
                [ConcurrencyStamp] [nvarchar](max) NULL,
                [PhoneNumber] [nvarchar](max) NULL,
                [PhoneNumberConfirmed] [bit] NOT NULL,
                [TwoFactorEnabled] [bit] NOT NULL,
                [LockoutEnd] [datetimeoffset](7) NULL,
                [LockoutEnabled] [bit] NOT NULL,
                [AccessFailedCount] [int] NOT NULL,
                CONSTRAINT [PK_AspNetUsers] PRIMARY KEY CLUSTERED ([Id] ASC)
            );

            CREATE TABLE [dbo].[AspNetUserRoles] (
                [UserId] [nvarchar](450) NOT NULL,
                [RoleId] [nvarchar](450) NOT NULL,
                CONSTRAINT [PK_AspNetUserRoles] PRIMARY KEY CLUSTERED ([UserId] ASC, [RoleId] ASC),
                CONSTRAINT [FK_AspNetUserRoles_AspNetUsers_UserId] FOREIGN KEY([UserId])
                    REFERENCES [dbo].[AspNetUsers] ([Id]) ON DELETE CASCADE
            );
            """;

        DatabaseSchema schema = parser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(2);
        assertThat(schema.getRelationships()).hasSize(1);

        Table userTable = schema.findTable("AspNetUsers");
        assertThat(userTable).isNotNull();
        assertThat(userTable.getColumns()).hasSizeGreaterThan(10);

        Table userRolesTable = schema.findTable("AspNetUserRoles");
        assertThat(userRolesTable).isNotNull();
        assertThat(userRolesTable.getIndexes()).anyMatch(idx ->
                idx.getName().equals("PK_AspNetUserRoles"));
    }
}