using System;
using System.Configuration;
using System.Linq;
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
                // Connect to existing Docker database (no setup needed)
                using (var context = new AppDbContext())
                {
                    Console.WriteLine("Connecting to Docker SQL Server...");
                    Console.WriteLine($"Connection string: {context.Database.Connection.ConnectionString}");
                    
                    // Test basic connection
                    try
                    {
                        context.Database.Connection.Open();
                        Console.WriteLine("✓ Database connection successful");
                        context.Database.Connection.Close();
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"✗ Database connection failed: {ex.Message}");
                        return;
                    }
                    
                    // Check if database exists
                    bool dbExists = context.Database.Exists();
                    Console.WriteLine($"Database exists: {dbExists}");
                    
                    if (!dbExists)
                    {
                        Console.WriteLine("ERROR: TestEcommerceApp database not found. Check Docker container.");
                        Console.WriteLine("\nPress any key to exit...");
                        Console.ReadLine();
                        return;
                    }
                    
                    // Verify connection and data exists
                    var customerCount = context.Customer.Count();
                    Console.WriteLine($"Found {customerCount} customers in database.");
                    
                    if (customerCount == 0)
                    {
                        Console.WriteLine("WARNING: No customers found. Ensure Docker container is running with init scripts.");
                        Console.WriteLine("\nPress any key to exit...");
                        Console.ReadLine();
                        return;
                    }

                    // Run usage simulation only
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