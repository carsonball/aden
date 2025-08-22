package org.carball.aden.parser;

import org.carball.aden.model.schema.Column;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.model.schema.Relationship;
import org.carball.aden.model.schema.RelationshipType;
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

    @Test
    public void shouldParseSimpleTable() {
        String ddl = "CREATE TABLE Customer (Id int PRIMARY KEY, Name nvarchar(100))";
        DatabaseSchema schema = SchemaParser.parseDDL(ddl);
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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(1);
        // OrderItem is not considered a pure junction table because it has additional data (Quantity)
        // So it should only have the 2 MANY_TO_ONE relationships, no MANY_TO_MANY
        assertThat(schema.getRelationships()).hasSize(2);
        
        // OrderItem references primary keys, so should be MANY_TO_ONE relationships
        schema.getRelationships().forEach(rel -> 
            assertThat(rel.getType()).isEqualTo(RelationshipType.MANY_TO_ONE));

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

        DatabaseSchema schema = SchemaParser.parseDDL(schemaFile);

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

        assertThatThrownBy(() -> SchemaParser.parseDDL(invalidDdl))
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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

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

    @Test
    public void shouldParseInlineForeignKeyWithExplicitColumn() {
        String ddl = """
            CREATE TABLE Department (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE Employee (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL,
                DeptId int REFERENCES Department(Id)
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(2);
        assertThat(schema.getRelationships()).hasSize(1);

        Relationship relationship = schema.getRelationships().get(0);
        assertThat(relationship.getFromTable()).isEqualTo("Employee");
        assertThat(relationship.getFromColumn()).isEqualTo("DeptId");
        assertThat(relationship.getToTable()).isEqualTo("Department");
        assertThat(relationship.getToColumn()).isEqualTo("Id");
    }

    @Test
    public void shouldParseInlineForeignKeyWithDefaultColumn() {
        String ddl = """
            CREATE TABLE Category (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE Product (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL,
                CategoryId int REFERENCES Category
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(2);
        assertThat(schema.getRelationships()).hasSize(1);

        Relationship relationship = schema.getRelationships().get(0);
        assertThat(relationship.getFromTable()).isEqualTo("Product");
        assertThat(relationship.getFromColumn()).isEqualTo("CategoryId");
        assertThat(relationship.getToTable()).isEqualTo("Category");
        assertThat(relationship.getToColumn()).isEqualTo("Id");
    }

    @Test
    public void shouldIgnoreStandaloneReferencesKeyword() {
        String ddl = """
            CREATE TABLE TestTable (
                Id int PRIMARY KEY,
                Name nvarchar(100) REFERENCES,
                Value int NOT NULL
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getTables()).hasSize(1);
        assertThat(schema.getRelationships()).hasSize(0);

        Table table = schema.getTables().get(0);
        assertThat(table.getName()).isEqualTo("TestTable");
        assertThat(table.getColumns()).hasSize(3);
    }

    @Test
    public void shouldDetectManyToOneRelationship() {
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

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getRelationships()).hasSize(1);
        Relationship relationship = schema.getRelationships().get(0);
        assertThat(relationship.getType()).isEqualTo(RelationshipType.MANY_TO_ONE);
        assertThat(relationship.getFromTable()).isEqualTo("Orders");
        assertThat(relationship.getToTable()).isEqualTo("Customer");
    }

    @Test
    public void shouldDetectOneToOneRelationship() {
        String ddl = """
            CREATE TABLE Account (
                Id int PRIMARY KEY,
                Username nvarchar(50) NOT NULL
            );
            
            CREATE TABLE AccountProfile (
                Id int PRIMARY KEY,
                AccountId int UNIQUE REFERENCES Account(Id),
                Bio nvarchar(500)
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getRelationships()).hasSize(1);
        Relationship relationship = schema.getRelationships().get(0);
        assertThat(relationship.getType()).isEqualTo(RelationshipType.ONE_TO_ONE);
        assertThat(relationship.getFromTable()).isEqualTo("AccountProfile");
        assertThat(relationship.getToTable()).isEqualTo("Account");
    }

    @Test
    public void shouldDetectManyToManyRelationship() {
        String ddl = """
            CREATE TABLE Student (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE Course (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE StudentCourse (
                StudentId int NOT NULL REFERENCES Student(Id),
                CourseId int NOT NULL REFERENCES Course(Id),
                PRIMARY KEY (StudentId, CourseId)
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        // Should have 2 MANY_TO_ONE relationships (junction to entities) + 2 MANY_TO_MANY relationships (between entities)
        assertThat(schema.getRelationships()).hasSize(4);
        
        // Check junction table relationships - these should be MANY_TO_ONE because it's a junction table
        long junctionRelationships = schema.getRelationships().stream()
                .filter(r -> r.getType() == RelationshipType.MANY_TO_ONE && r.getFromTable().equals("StudentCourse"))
                .count();
        assertThat(junctionRelationships).isEqualTo(2);
        
        // Check MANY_TO_MANY relationships between Student and Course
        assertThat(schema.getRelationships().stream()
            .anyMatch(r -> r.getType() == RelationshipType.MANY_TO_MANY && 
                          r.getFromTable().equals("Student") && 
                          r.getToTable().equals("Course"))).isTrue();
        
        assertThat(schema.getRelationships().stream()
            .anyMatch(r -> r.getType() == RelationshipType.MANY_TO_MANY && 
                          r.getFromTable().equals("Course") && 
                          r.getToTable().equals("Student"))).isTrue();
    }

    @Test
    public void shouldDetectMultipleRelationshipTypes() {
        String ddl = """
            CREATE TABLE Company (
                Id int PRIMARY KEY,
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE Department (
                Id int PRIMARY KEY,
                CompanyId int REFERENCES Company(Id),
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE Employee (
                Id int PRIMARY KEY,
                DepartmentId int REFERENCES Department(Id),
                Name nvarchar(100) NOT NULL
            );
            
            CREATE TABLE EmployeeProfile (
                EmployeeId int PRIMARY KEY REFERENCES Employee(Id),
                Bio nvarchar(500)
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getRelationships()).hasSize(3);
        
        // Check that Company -> Department and Department -> Employee are MANY_TO_ONE
        long manyToOneCount = schema.getRelationships().stream()
                .filter(r -> r.getType() == RelationshipType.MANY_TO_ONE)
                .count();
        assertThat(manyToOneCount).isEqualTo(2);
        
        // EmployeeProfile should be ONE_TO_ONE (PK is also FK)
        Relationship profileRel = schema.getRelationships().stream()
            .filter(r -> r.getFromTable().equals("EmployeeProfile"))
            .findFirst().orElse(null);
        assertThat(profileRel).isNotNull();
        assertThat(profileRel.getType()).isEqualTo(RelationshipType.ONE_TO_ONE);
    }

    @Test
    public void shouldUseSourceAndTargetConstraintsForRelationshipType() {
        String ddl = """
            CREATE TABLE Person (
                Id int PRIMARY KEY,
                Username nvarchar(50) UNIQUE NOT NULL,
                Email nvarchar(100) NOT NULL
            );
            
            CREATE TABLE Profile (
                Id int PRIMARY KEY,
                PersonId int REFERENCES Person(Id),
                Bio nvarchar(500)
            );
            
            CREATE TABLE ContactInfo (
                Id int PRIMARY KEY,
                PersonEmail nvarchar(100) UNIQUE REFERENCES Person(Email),
                PhoneNumber nvarchar(20)
            );
            """;

        DatabaseSchema schema = SchemaParser.parseDDL(ddl);

        assertThat(schema.getRelationships()).hasSize(2);
        
        // Source not unique, Target unique (PK) -> MANY_TO_ONE
        Relationship pkRelationship = schema.getRelationships().stream()
            .filter(r -> r.getFromColumn().equals("PersonId"))
            .findFirst().orElse(null);
        assertThat(pkRelationship).isNotNull();
        assertThat(pkRelationship.getType()).isEqualTo(RelationshipType.MANY_TO_ONE);
        
        // Source unique (UNIQUE), Target not unique -> ONE_TO_MANY
        Relationship oneToManyRelationship = schema.getRelationships().stream()
            .filter(r -> r.getFromColumn().equals("PersonEmail"))
            .findFirst().orElse(null);
        assertThat(oneToManyRelationship).isNotNull();
        assertThat(oneToManyRelationship.getType()).isEqualTo(RelationshipType.ONE_TO_MANY);
    }
}