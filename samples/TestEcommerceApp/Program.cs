using System;
using System.Configuration;
using System.Data.Entity;
using TestEcommerceApp.Models;
using TestEcommerceApp.Services;

namespace TestEcommerceApp
{
    class Program
    {
        static void Main(string[] args)
        {
            Console.WriteLine("TestEcommerceApp - Database Setup and Usage Simulation");
            Console.WriteLine("=====================================================");

            try
            {
                // Force close any existing connections before dropping database
                using (var connection = new System.Data.SqlClient.SqlConnection())
                {
                    var connectionString = ConfigurationManager.ConnectionStrings["DefaultConnection"].ConnectionString;
                    connection.ConnectionString = connectionString.Replace("TestEcommerceApp", "master");
                    connection.Open();
                    
                    using (var command = connection.CreateCommand())
                    {
                        command.CommandText = @"
                            IF DB_ID('TestEcommerceApp') IS NOT NULL
                            BEGIN
                                ALTER DATABASE TestEcommerceApp SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                            END";
                        command.ExecuteNonQuery();
                        
                        command.CommandText = @"
                            IF DB_ID('TestEcommerceApp') IS NOT NULL
                            BEGIN
                                DROP DATABASE TestEcommerceApp;
                            END";
                        command.ExecuteNonQuery();
                    }
                }

                // Initialize database - drop and recreate for fresh data each run
                Database.SetInitializer(new DropCreateDatabaseAlways<AppDbContext>());

                using (var context = new AppDbContext())
                {
                    // Ensure database is created
                    context.Database.Initialize(force: false);
                    Console.WriteLine("Database initialized successfully.");

                    // Enable Query Store for usage metrics collection
                    Console.WriteLine("Enabling Query Store...");
                    using (var connection = new System.Data.SqlClient.SqlConnection())
                    {
                        var connectionString = ConfigurationManager.ConnectionStrings["DefaultConnection"].ConnectionString;
                        connection.ConnectionString = connectionString;
                        connection.Open();
                        
                        using (var command = connection.CreateCommand())
                        {
                            command.CommandText = @"
                                ALTER DATABASE TestEcommerceApp SET QUERY_STORE = ON (
                                    OPERATION_MODE = READ_WRITE,
                                    CLEANUP_POLICY = (STALE_QUERY_THRESHOLD_DAYS = 30),
                                    DATA_FLUSH_INTERVAL_SECONDS = 60,
                                    INTERVAL_LENGTH_MINUTES = 1,
                                    MAX_STORAGE_SIZE_MB = 100,
                                    QUERY_CAPTURE_MODE = ALL,
                                    SIZE_BASED_CLEANUP_MODE = AUTO,
                                    QUERY_CAPTURE_POLICY = (
                                        EXECUTION_COUNT = 1,
                                        TOTAL_COMPILE_CPU_TIME_MS = 1,
                                        TOTAL_EXECUTION_CPU_TIME_MS = 1
                                    )
                                )";
                            command.ExecuteNonQuery();
                        }
                    }
                    Console.WriteLine("Query Store enabled successfully.");

                    // Seed test data
                    var seeder = new TestDataSeeder(context);
                    seeder.SeedData();
                    Console.WriteLine("Test data seeded successfully.");

                    // Run usage simulation
                    var simulator = new UsageSimulator(context);
                    simulator.RunSimulation();
                    Console.WriteLine("Usage simulation completed.");
                }

                // Force Query Store to flush data immediately
                Console.WriteLine("Flushing Query Store data...");
                using (var connection = new System.Data.SqlClient.SqlConnection())
                {
                    var connectionString = ConfigurationManager.ConnectionStrings["DefaultConnection"].ConnectionString;
                    connection.ConnectionString = connectionString;
                    connection.Open();
                    
                    using (var command = connection.CreateCommand())
                    {
                        command.CommandText = "EXEC sp_query_store_flush_db";
                        command.ExecuteNonQuery();
                    }
                }
                Console.WriteLine("Query Store data flushed.");

                Console.WriteLine("\nApplication completed successfully.");
                Console.WriteLine("Check SQL Server Query Store for usage metrics.");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error: {ex.Message}");
                Console.WriteLine($"Stack trace: {ex.StackTrace}");
                Console.WriteLine("\nPress any key to exit...");
                Console.ReadKey();
                Environment.Exit(1);
            }

            Console.WriteLine("Press any key to exit...");
            Console.ReadKey();
        }
    }
}