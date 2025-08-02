# CLAUDE.md - AI Assistant Guidelines

## Overview

This document provides guidelines for working with Claude on the .NET Framework to AWS NoSQL Migration Analyzer project. It ensures consistent, efficient, and high-quality AI-assisted development.

## Project Context

**Project**: .NET Framework to AWS NoSQL Migration Analyzer  
**Purpose**: Analyze legacy .NET applications and recommend AWS NoSQL migration strategies  
**Tech Stack**: Java 17, Maven, JSqlParser, OpenAI API, SQL Server JDBC Driver  
**Key Components**: SQL DDL Parser, Entity Framework Parser, LINQ Analyzer, Query Store Analyzer, AI Recommendation Engine (Batched), Migration Pattern Analyzer

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

## Project-Specific Guidelines

### 1. Parser Modifications

- Always update corresponding tests
- Consider SQL dialect variations
- Maintain JSqlParser integration
- Document regex patterns
- Entity relationships now tracked from multiple sources (DB schema, EF navigation properties, query patterns)

### 2. AI Integration

- Mock OpenAI responses in tests
- Handle API failures gracefully
- Consider response parsing variations
- Document prompt changes
- Support -Dskip.ai=true for development/testing
- All entities sent in single batched request for holistic recommendations
- Prompts include database overview, relationships, and cross-entity patterns
- Response parser handles entity-specific sections within batch response

### 3. Analysis Engine

- Maintain scoring consistency
- Document business rules
- Consider edge cases in pattern matching
- Update complexity calculations carefully
- Query Store integration provides production metrics
- Relationship detection from DB schema + EF models + query patterns
- Thresholds configurable via profiles (default, aggressive, startup-aggressive)

## Performance Considerations

When working on performance-sensitive code:
- State performance requirements upfront
- Provide current benchmarks
- Specify acceptable trade-offs
- Note memory constraints

## Security Considerations

For security-related changes:
- Highlight sensitive data handling
- Note authentication/authorization needs
- Specify input validation requirements
- Consider injection attack vectors

### 4. Query Store Integration (Secure File-Based Approach)

- **Security-First Design**: Uses exported JSON files instead of direct database connections
- Requires SQL Server 2016+ with Query Store enabled for data export
- Provides production usage metrics: execution counts, read/write ratios, co-access patterns
- Enhances AI recommendations with real-world data
- Export files created using provided T-SQL scripts (scripts/export-query-store.sql)
- PowerShell automation available (scripts/Setup-QueryStoreExport.ps1)
- Eliminates need for customers to provide database credentials to external tools

## Quick Reference

### Common Files
- `SchemaParser.java` - SQL DDL parsing with JSqlParser
- `EFModelParser.java` - C# Entity Framework model detection
- `LinqAnalyzer.java` - LINQ query pattern analysis
- `DotNetPatternAnalyzer.java` - Pattern correlation logic with relationship tracking
- `RecommendationEngine.java` - Batched AI integration for holistic recommendations
- `QueryStoreFileConnector.java` - Secure Query Store data import from JSON files
- `QueryStoreAnalyzer.java` - Production metrics analysis from Query Store
- `DotNetAnalyzerCLI.java` - Command line interface with --query-store-file support
- `MigrationReport.java` - Output generation in JSON/Markdown formats
- `EntityUsageProfile.java` - Tracks entity relationships and usage patterns
- `scripts/export-query-store.sql` - T-SQL script for secure Query Store export
- `scripts/Setup-QueryStoreExport.ps1` - PowerShell automation for export setup

### Key Patterns
- Navigation properties indicate relationships (tracked in EntityUsageProfile)
- Include() calls suggest denormalization candidates
- High frequency + always together = good migration candidate
- Complex relationships → DocumentDB, Simple → DynamoDB, Graph patterns → Neptune
- Package-private methods for test access (remove 'private' modifier)
- Query Store co-access patterns reveal production relationships
- Related entities populated from: DB foreign keys + EF navigation + query patterns
- Batched AI requests consider all entities together for single-table design

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

# Test with Query Store integration (secure file-based approach, no OpenAI key)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --query-store-file query-store-export.json

# Run with real AI recommendations
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/

# Run with AI + Query Store for complete analysis (secure approach)
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --query-store-file query-store-export.json

# Use aggressive profile for small applications
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/ --profile startup-aggressive
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
  --target            AWS target: dynamodb|documentdb|neptune|all (default: all)
  --complexity        Include only: low|medium|high|all (default: all)
  --query-store-file  JSON file exported from Query Store (secure alternative)
  --profile           Migration profile: default|aggressive|startup-aggressive|discovery
  --verbose, -v       Enable verbose output
  --help              Show help information
  --help-profiles     Show available migration profiles
  --help-thresholds   Show threshold configuration options
```

### Environment Variables
- `OPENAI_API_KEY` - OpenAI API key for AI recommendations

### Query Store Export Process
1. Run T-SQL export script: `scripts/export-query-store.sql`
2. Save JSON output to file (e.g., `query-store-export.json`)
3. Use with analyzer: `--query-store-file query-store-export.json`
4. Optional: Use PowerShell automation: `scripts/Setup-QueryStoreExport.ps1`

---

*Remember: Clear communication leads to better code. When in doubt, over-communicate context and constraints.*