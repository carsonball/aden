# .NET Framework to AWS NoSQL Migration Analyzer

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6%2B-blue)](https://maven.apache.org/)

An intelligent command-line tool that analyzes legacy .NET Framework applications with Entity Framework and SQL Server, then recommends optimal AWS NoSQL migration strategies using AI-powered analysis.

## 🎯 Problem Statement

Many organizations are stuck with legacy .NET Framework applications that:
- Use expensive SQL Server licenses
- Run on end-of-life .NET Framework versions
- Have complex Entity Framework relationships that make migration daunting
- Need modernization but lack clear migration paths

This tool analyzes your codebase and provides specific, actionable recommendations for migrating to AWS NoSQL services (DynamoDB, DocumentDB, Neptune).

## ✨ Features

- **SQL Schema Analysis**: Parses SQL Server DDL to understand database structure
- **Entity Framework Detection**: Identifies navigation properties and relationships in C# code
- **LINQ Pattern Analysis**: Detects eager loading patterns (`.Include()`) that indicate denormalization opportunities
- **AI-Powered Recommendations**: Uses OpenAI GPT-4 to generate specific NoSQL designs
- **Cost Estimation**: Provides estimated savings compared to SQL Server
- **Multiple Output Formats**: JSON for automation, Markdown for human review

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- OpenAI API key (optional - can run without AI features)
- .NET Framework application source code with Entity Framework

## 🚀 Quick Start

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/dotnet-aws-migration-analyzer.git
cd dotnet-aws-migration-analyzer

# Build the project
mvn clean package

# The executable JAR will be in target/
```

### Basic Usage

```bash
# Analyze a .NET application with default settings
java -jar target/dotnet-analyzer.jar schema.sql ./src/YourApp/

# Skip AI recommendations (no API key needed)
java -Dskip.ai=true -jar target/dotnet-analyzer.jar schema.sql ./src/YourApp/

# Generate both JSON and Markdown reports
java -jar target/dotnet-analyzer.jar schema.sql ./src/YourApp/ --format both
```

### Smart Configuration with Profiles

The analyzer includes intelligent configuration profiles that adapt to different application contexts:

```bash
# For small applications and prototypes
java -jar target/dotnet-analyzer.jar schema.sql ./src/ --profile startup-aggressive

# For enterprise applications (conservative analysis)
java -jar target/dotnet-analyzer.jar schema.sql ./src/ --profile enterprise-conservative

# For discovery of all potential patterns
java -jar target/dotnet-analyzer.jar schema.sql ./src/ --profile discovery

# Industry-specific optimizations
java -jar target/dotnet-analyzer.jar schema.sql ./src/ --profile retail
java -jar target/dotnet-analyzer.jar schema.sql ./src/ --profile healthcare
java -jar target/dotnet-analyzer.jar schema.sql ./src/ --profile financial
```

### Custom Threshold Configuration

```bash
# Override specific thresholds for your application
java -jar target/dotnet-analyzer.jar schema.sql ./src/ \
  --thresholds.high-frequency 25 \
  --thresholds.medium-frequency 10

# Use environment variables for threshold configuration
export ADEN_HIGH_FREQUENCY_THRESHOLD=25
export ADEN_MEDIUM_FREQUENCY_THRESHOLD=10
java -jar target/dotnet-analyzer.jar schema.sql ./src/
```

### Getting Help

```bash
# See all available profiles
java -jar target/dotnet-analyzer.jar --help-profiles

# See detailed threshold configuration options
java -jar target/dotnet-analyzer.jar --help-thresholds

# General help
java -jar target/dotnet-analyzer.jar --help
```

## 🔧 Configuration

### Environment Variables

```bash
# Required for AI recommendations
export OPENAI_API_KEY=sk-your-api-key

# Skip AI features
export SKIP_AI=true

# Threshold configuration
export ADEN_HIGH_FREQUENCY_THRESHOLD=50
export ADEN_MEDIUM_FREQUENCY_THRESHOLD=20
export ADEN_HIGH_READ_WRITE_RATIO=10.0
export ADEN_COMPLEX_RELATIONSHIP_PENALTY=10
export ADEN_COMPLEXITY_PENALTY_MULTIPLIER=1.5
export ADEN_MINIMUM_MIGRATION_SCORE=30
```

### Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--output, -o` | Output file for recommendations | `recommendations.json` |
| `--format, -f` | Output format: `json`, `markdown`, `both` | `json` |
| `--api-key` | OpenAI API key | Environment variable |
| `--target` | AWS target services: `dynamodb`, `documentdb`, `neptune`, `all` | `all` |
| `--complexity` | Filter by complexity: `low`, `medium`, `high`, `all` | `all` |
| `--verbose, -v` | Enable verbose output | `false` |
| `--debug` | Enable debug logging | `false` |

## 📊 How It Works

### 1. Schema Parsing
The tool uses JSqlParser to analyze your SQL Server schema:
```sql
CREATE TABLE Customer (
    Id int PRIMARY KEY,
    Name nvarchar(100) NOT NULL
);

CREATE TABLE Orders (
    Id int PRIMARY KEY,
    CustomerId int REFERENCES Customer(Id)
);
```

### 2. Entity Framework Analysis
Detects navigation properties in your C# models:
```csharp
public class Customer {
    public int Id { get; set; }
    public string Name { get; set; }
    public virtual ICollection<Order> Orders { get; set; }  // <- Detected!
}
```

### 3. LINQ Pattern Detection
Identifies eager loading patterns:
```csharp
var customers = context.Customers
    .Include(c => c.Orders)              // <- Suggests denormalization
    .Include(c => c.Orders.Select(o => o.OrderItems))
    .ToList();
```

### 4. AI-Powered Recommendations
Generates specific NoSQL designs:
- **DynamoDB**: Single-table design with partition/sort keys
- **DocumentDB**: Document structure with embedded relationships
- **Neptune**: Graph database for complex many-to-many relationships

## 📁 Sample Application

A complete sample application is included in `samples/TestEcommerceApp/`:

```bash
# Run the analyzer on the sample app
java -Dskip.ai=true -jar target/dotnet-analyzer.jar \
    samples/TestEcommerceApp/schema.sql \
    samples/TestEcommerceApp/
```

## 📈 Example Output

### JSON Output
```json
{
  "migrationCandidates": [{
    "primaryEntity": "Customer",
    "relatedEntities": ["Orders", "OrderItems"],
    "complexity": "MEDIUM",
    "score": 135,
    "scoreInterpretation": "Strong candidate - high priority",
    "recommendation": {
      "targetService": "DynamoDB",
      "partitionKey": {
        "attribute": "customerId",
        "type": "S"
      },
      "estimatedCostSaving": "65% reduction vs SQL Server"
    }
  }]
}
```

### Markdown Report
The tool generates comprehensive Markdown reports with:
- Executive summary
- Migration score guide with priority levels
- Detailed migration recommendations
- Key design specifications
- Cost analysis
- Next steps

#### Migration Scoring System
The tool uses a comprehensive scoring system (0-190 points) to prioritize migrations:

| Score Range | Priority | Description |
|-------------|----------|-------------|
| 150+ | 🔴 **Immediate** | Excellent candidate - migrate immediately |
| 100-149 | 🟠 **High** | Strong candidate - high priority |
| 60-99 | 🟡 **Medium** | Good candidate - medium priority |
| 30-59 | 🟢 **Low** | Fair candidate - low priority |
| 0-29 | ⚪ **Reconsider** | Poor candidate - reconsider approach |

Scores are calculated based on:
- Eager loading frequency (up to 100 points)
- Related entities always loaded together (up to 30 points)
- Read-heavy access patterns (up to 20 points)
- Query complexity (up to 25 points)
- Bonus for simple key-based access (15 points)
- Penalties for circular references or complex relationships

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run minimal test suite
mvn test -Dtest=MinimalTests

# Run with coverage
mvn test jacoco:report
```

## 🏗️ Architecture

```
┌─────────────────────┐    ┌────────────────────┐    ┌─────────────────────┐
│   Data Ingestion    │ -> │  Analysis Engine   │ -> │ Recommendation      │
│                     │    │                    │    │ Engine (AI)         │
│  • Schema Parser    │    │ • Pattern Correlator│    │ • Target Selection  │
│  • EF Model Parser  │    │ • Complexity Scorer │    │ • Schema Generation │
│  • LINQ Analyzer    │    │ • Usage Analyzer    │    │ • Cost Estimation   │
└─────────────────────┘    └────────────────────┘    └─────────────────────┘
```

## 🤝 Contributing

See [CLAUDE.md](CLAUDE.md) for guidelines on working with AI assistance on this project.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request


## 🙏 Acknowledgments

- [JSqlParser](https://github.com/JSQLParser/JSqlParser) for SQL parsing
- [OpenAI](https://openai.com/) for AI recommendations
- [Lombok](https://projectlombok.org/) for reducing boilerplate

## 🚧 Roadmap

- [ ] Support for EF Core patterns
- [ ] Production log analysis for actual usage metrics
- [ ] Azure Cosmos DB recommendations
- [ ] Automated migration script generation
- [ ] Visual Studio extension

---

Built with ☕ and 🤖 to help modernize legacy .NET applications