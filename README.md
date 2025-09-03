# .NET Framework to AWS NoSQL Migration Analyzer

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6%2B-blue)](https://maven.apache.org/)

Analyzes .NET Framework applications and SQL Server usage patterns to generate AI-powered AWS NoSQL migration recommendations with infrastructure-as-code.

## Key Features

- **Static Code Analysis**: Parses C# models and LINQ queries to identify data access patterns
- **SQL Server Query Store Integration**: Analyzes real production query patterns and co-access relationships  
- **AI Migration Recommendations**: GPT-4 powered analysis generates specific DynamoDB/DocumentDB/Neptune designs
- **Terraform Generation**: Creates ready-to-deploy AWS infrastructure code

## Quick Start

```bash
# Build
mvn clean package

# Basic analysis (static code only)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar schema.sql ./src/

# With Query Store production data
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar schema.sql ./src/ --query-store-file query-store-export.json

# Full AI analysis + Terraform generation
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar schema.sql ./src/ --query-store-file query-store-export.json --terraform
```

## Configuration

```bash
# Environment Variables
export OPENAI_API_KEY=sk-your-api-key  # For AI recommendations

# Key Options  
--output, -o          Output file (default: recommendations.json)
--format, -f          json|markdown|both (default: json)  
--query-store-file    Query Store export JSON file
--terraform           Generate AWS infrastructure code
--thresholds          Production usage & co-access sensitivity tuning
--verbose, -v         Detailed logging

# Threshold Profiles (samples/ directory)
--thresholds samples/thresholds-default.yml       # Balanced discovery
--thresholds samples/thresholds-aggressive.yml    # Maximum candidates  
--thresholds samples/thresholds-conservative.yml  # Enterprise-safe
```

## How It Works

1. **Parses SQL schema** and C# Entity Framework models to map relationships
2. **Analyzes LINQ patterns** (`.Include()` calls) to identify denormalization candidates  
3. **Processes Query Store data** to understand real production co-access patterns
4. **Feeds consolidated metrics to GPT-4** for tailored NoSQL schema recommendations
5. **Generates Terraform** for immediate AWS deployment

## Sample Usage

```bash
# Try the included test application
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar \
    samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/
```

## Example Output

```json
{
  "migrationCandidates": [{
    "primaryEntity": "Customer", 
    "relatedEntities": ["Orders", "OrderItems"],
    "score": 135,
    "scoreInterpretation": "Strong candidate - high priority",
    "recommendation": {
      "targetService": "DynamoDB",
      "partitionKey": "customerId",
      "estimatedCostSaving": "65% reduction vs SQL Server"
    }
  }]
}
```

**Migration Priority Scores:** 150+ (immediate), 100-149 (high), 60-99 (medium), 30-59 (low)

## Testing

```bash
mvn test                    # Run all tests
mvn test -Dtest=MinimalTests # Quick smoke tests
```

## Requirements

- Java 17+
- Maven 3.6+
- OpenAI API key (for AI features)

**Development:** MCP server available at `aden-dev-mcp/` for enhanced tooling integration