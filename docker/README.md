# Docker Development Environment

This directory provides a containerized SQL Server environment for testing the .NET Framework to AWS NoSQL Migration Analyzer.

## Quick Start

1. **Start SQL Server container:**
   ```bash
   docker-compose up -d
   ```

2. **Verify container is running:**
   ```bash
   docker-compose ps
   ```

## Container Details

- **Image:** `mcr.microsoft.com/mssql/server:2022-latest`
- **Database:** `TestEcommerceApp`
- **Port:** `1433`
- **Credentials:** `sa` / `TestPassword123!`
- **Query Store:** Enabled for usage analysis

## Initialization Scripts

The container automatically runs SQL scripts in `/docker/init/`:

1. `01-create-database.sql` - Creates database and enables Query Store
2. `02-create-schema.sql` - Creates tables (Customer, CustomerProfile, Order, etc.)
3. `03-seed-data.sql` - Inserts test data (10 customers with profiles)

## Connection Strings

### Java (JDBC)
```
jdbc:sqlserver://localhost:1433;databaseName=TestEcommerceApp;user=sa;password=TestPassword123!;trustServerCertificate=true;encrypt=false
```

### C# (.NET Framework)
```xml
<add name="DefaultConnection" 
     connectionString="Server=localhost;Database=TestEcommerceApp;User Id=sa;Password=TestPassword123!;TrustServerCertificate=True;" 
     providerName="System.Data.SqlClient" />
```

## Management

- **Stop container:** `docker-compose down`
- **View logs:** `docker-compose logs sqlserver`
- **Connect via SSMS:** Server: `localhost`, Auth: SQL Server, User: `sa`, Password: `TestPassword123!`
- **Reset data:** `docker-compose down -v && docker-compose up -d`

## Troubleshooting

- **Container won't start:** Check port 1433 isn't in use: `netstat -an | grep 1433`
- **Connection refused:** Wait 10-15 seconds for SQL Server to fully initialize
- **Permission denied:** Ensure Docker has access to bind port 1433