# Test E-commerce Application

This is a sample .NET Framework 4.7.2 application with Entity Framework 6.4.4 used to test the migration analyzer.

## Structure

- **Models/** - Entity Framework models with navigation properties
- **Services/** - Data access patterns with eager loading
- **schema.sql** - SQL Server database schema

## Usage

Run the analyzer against this sample:

```bash
cd ../..
java -jar target/dotnet-analyzer.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/