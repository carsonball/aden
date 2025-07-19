# CLAUDE.md - AI Assistant Guidelines

## Overview

This document provides guidelines for working with Claude on the .NET Framework to AWS NoSQL Migration Analyzer project. It ensures consistent, efficient, and high-quality AI-assisted development.

## Project Context

**Project**: .NET Framework to AWS NoSQL Migration Analyzer  
**Purpose**: Analyze legacy .NET applications and recommend AWS NoSQL migration strategies  
**Tech Stack**: Java 17, Maven, JSqlParser, OpenAI API  
**Key Components**: SQL DDL Parser, Entity Framework Parser, LINQ Analyzer, AI Recommendation Engine

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

### 2. AI Integration

- Mock OpenAI responses in tests
- Handle API failures gracefully
- Consider response parsing variations
- Document prompt changes
- Support -Dskip.ai=true for development/testing

### 3. Analysis Engine

- Maintain scoring consistency
- Document business rules
- Consider edge cases in pattern matching
- Update complexity calculations carefully

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

## Quick Reference

### Common Files
- `SchemaParser.java` - SQL DDL parsing with JSqlParser
- `EFModelParser.java` - C# Entity Framework model detection
- `LinqAnalyzer.java` - LINQ query pattern analysis
- `DotNetPatternAnalyzer.java` - Pattern correlation logic
- `RecommendationEngine.java` - AI integration for recommendations
- `DotNetAnalyzerCLI.java` - Command line interface and main entry point
- `MigrationReport.java` - Output generation in JSON/Markdown formats

### Key Patterns
- Navigation properties indicate relationships
- Include() calls suggest denormalization candidates
- High frequency + always together = good migration candidate
- Complex relationships → DocumentDB, Simple → DynamoDB, Graph patterns → Neptune
- Package-private methods for test access (remove 'private' modifier)

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

# Run with real AI recommendations
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/
```

---

*Remember: Clear communication leads to better code. When in doubt, over-communicate context and constraints.*