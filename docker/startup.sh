#!/bin/bash

# Wait for SQL Server to be ready
echo "Waiting for SQL Server to start..."
sleep 15

# Check if database already exists
DB_EXISTS=$(sqlcmd -S localhost -U sa -P TestPassword123! -C -Q "SELECT DB_ID('TestEcommerceApp')" -h -1 2>/dev/null | tr -d ' \n\r')

if [ "$DB_EXISTS" = "NULL" ] || [ -z "$DB_EXISTS" ]; then
    echo "Initializing TestEcommerceApp database..."
    
    # Run initialization scripts
    sqlcmd -S localhost -U sa -P TestPassword123! -C -i /init-scripts/01-create-database.sql
    sqlcmd -S localhost -U sa -P TestPassword123! -C -i /init-scripts/02-create-schema.sql  
    sqlcmd -S localhost -U sa -P TestPassword123! -C -i /init-scripts/03-seed-data.sql
    
    echo "Database initialization completed."
else
    echo "TestEcommerceApp database already exists, skipping initialization."
fi