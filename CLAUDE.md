# CLAUDE.md - AI Assistant Guidelines

## Overview

This document provides guidelines for working with Claude on the .NET Framework to AWS NoSQL Migration Analyzer project. It ensures consistent, efficient, and high-quality AI-assisted development.

## Code Standards

### 1. Java Style Guide

- **Package**: All code under `org.carball.aden`
- **Naming**: CamelCase for classes, camelCase for methods/variables
- **Annotations**: Use Lombok where appropriate (@Data, @Builder, @Slf4j)
- **Logging**: Use SLF4J with appropriate levels (trace, debug, info, warn, error)

### 2. Testing Standards

```java
// Test naming: should[ExpectedBehavior]When[Condition]
@Test
public void shouldParseForeignKeyWhenReferencesClausePresent() {
    // Given - setup
    // When - action
    // Then - assertions with AssertJ
}
```

### 3. Documentation

```java
/**
 * Parses SQL DDL and extracts database schema.
 *
 * @param ddlContent SQL DDL statements
 * @return DatabaseSchema containing tables and relationships
 * @throws IllegalArgumentException if DDL is invalid
 */
public DatabaseSchema parseDDL(String ddlContent) {
```

## Security Considerations

For security-related changes:
- Highlight sensitive data handling
- Note authentication/authorization needs
- Specify input validation requirements
- Consider injection attack vectors

## Key Patterns

- Navigation properties indicate relationships (tracked in EntityUsageProfile)
- Include() calls suggest denormalization candidates
- High frequency + always together = good migration candidate
- Complex relationships → DocumentDB, Simple → DynamoDB, Graph patterns → Neptune
- Package-private methods for test access (remove 'private' modifier)
- Query Store co-access patterns reveal production relationships
- Related entities populated from: DB foreign keys + EF navigation + query patterns
- Batched AI requests consider all entities together for single-table design

### Query Store Export Procedure
```bash
# Generate Query Store data by running TestEcommerceApp simulation
cd samples/TestEcommerceApp
dotnet build
./bin/Debug/TestEcommerceApp.exe

# Export Query Store data to JSON (requires SQL Server connection)
# Using MCP tools (connection from samples/TestEcommerceApp/App.config):
# Server=localhost,1433;Database=TestEcommerceApp;User Id=sa;Password=TestPassword123!;TrustServerCertificate=True;MultipleActiveResultSets=True;
# Run scripts/export-query-store.sql to generate query-store-export.json
```

### Testing Commands
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SchemaParserTest

# Run minimal tests
mvn test -Dtest=MinimalTests

# Test with sample application (no OpenAI key needed)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/

# Test with Query Store integration (no OpenAI key needed)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --query-store-file query-store-export.json

# Test with Terraform generation (no OpenAI key needed)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --terraform

# Test with both Query Store and Terraform (no OpenAI key needed)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --query-store-file query-store-export.json --terraform

# Run with real AI recommendations
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/

# Run with AI + Query Store for complete analysis
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --query-store-file query-store-export.json

# Run with AI + Terraform generation
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --terraform

# Run with AI + Query Store + Terraform (complete analysis with infrastructure)
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --query-store-file query-store-export.json --terraform
```

### CLI Parameters
```
Required:
  schema-file         SQL Server schema DDL file (.sql)
  source-directory    Directory containing .NET Framework source code

Options:
  --output, -o        Output file for recommendations (default: recommendations.json)
  --format, -f        Output format: json|markdown|both (default: json)
  --api-key           OpenAI API key (or set OPENAI_API_KEY env var)
  --query-store-file  JSON file exported from Query Store (optional)
  --thresholds        YAML file with custom analysis thresholds (optional)
  --terraform         Generate Terraform infrastructure scripts
  --verbose, -v       Enable verbose output
  --help, -h          Show this help message
```

### Environment Variables
- `OPENAI_API_KEY` - OpenAI API key for AI recommendations

---

*Remember: Clear communication leads to better code. When in doubt, over-communicate context and constraints.*