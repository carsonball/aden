using System;
using System.Diagnostics;
using System.Linq;
using System.Threading;
using TestEcommerceApp.Services;

namespace TestEcommerceApp
{
    public class UsageSimulator
    {
        private readonly AppDbContext _context;
        private readonly Random _random;

        public UsageSimulator(AppDbContext context)
        {
            _context = context;
            _random = new Random();
        }

        public void RunSimulation()
        {
            Console.WriteLine("Running DynamoDB-focused simulation...");
            var totalStopwatch = Stopwatch.StartNew();
            
            // Basic verification that data exists
            var customerCount = _context.Customer.Count();
            Console.WriteLine($"Starting simulation with {customerCount} customers");
            
            if (customerCount == 0)
            {
                Console.WriteLine("No customers found - simulation cannot run");
                return;
            }

            // Run focused DynamoDB candidate scenario
            RunCustomerProfileScenario();
            
            // Force Query Store to capture all queries
            ForceQueryStoreFlush();
            
            totalStopwatch.Stop();
            Console.WriteLine($"\nTotal simulation time: {totalStopwatch.Elapsed:mm\\:ss}");
            Console.WriteLine("Query Store should now contain Customer+Profile usage patterns");
        }

        private void RunCustomerProfileScenario()
        {
            Console.WriteLine("\n=== Customer+Profile DynamoDB Candidate Simulation ===");
            var customerService = new CustomerService(_context);
            var customerIds = _context.Customer.Select(c => c.Id).ToList();
            var scenarioStopwatch = Stopwatch.StartNew();

            // HIGH FREQUENCY: Individual Customer+Profile queries (1000 calls)
            Console.WriteLine("Running GetCustomerWithProfile() calls (1000x)...");
            for (int i = 0; i < 1000; i++)
            {
                var randomCustomerId = customerIds[_random.Next(customerIds.Count)];
                customerService.GetCustomerWithProfile(randomCustomerId);
                
                if ((i + 1) % 100 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/1000 individual queries");
                }
                
                // Random delay to simulate realistic access patterns
                Thread.Sleep(_random.Next(50, 201)); // 50-200ms
            }

            // MEDIUM FREQUENCY: Batch Customer+Profile queries (100 calls)
            Console.WriteLine("Running GetCustomersWithProfiles() batch calls (100x)...");
            for (int i = 0; i < 100; i++)
            {
                var batchSize = _random.Next(5, 21); // 5-20 customers per batch
                customerService.GetCustomersWithProfiles(batchSize);
                
                if ((i + 1) % 20 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/100 batch queries");
                }
                
                Thread.Sleep(_random.Next(75, 251)); // Slightly longer for batch queries
            }

            // LOW FREQUENCY: Profile updates to show read:write ratio (50 calls)
            Console.WriteLine("Running UpdateCustomerProfile() calls (50x)...");
            for (int i = 0; i < 50; i++)
            {
                var randomCustomerId = customerIds[_random.Next(customerIds.Count)];
                // Simulate profile update - phone number change
                var newPhone = $"555-{_random.Next(100, 1000)}-{_random.Next(1000, 10000)}";
                customerService.UpdateCustomerProfile(randomCustomerId, address: null, phoneNumber: newPhone);
                
                if ((i + 1) % 10 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/50 profile updates");
                }
                
                Thread.Sleep(_random.Next(100, 301)); // Updates take longer
            }

            scenarioStopwatch.Stop();
            Console.WriteLine($"\nCustomer+Profile scenario completed in: {scenarioStopwatch.Elapsed:mm\\:ss}");
            Console.WriteLine("Expected Query Store results:");
            Console.WriteLine("  - Customer+Profile queries: 1,150 total executions");
            Console.WriteLine("  - Always-together pattern: 95%+ of queries include both tables");
            Console.WriteLine("  - Read:Write ratio: 22:1 (1,100 reads : 50 writes)");
            Console.WriteLine("  - High-priority DynamoDB candidate pattern demonstrated");
        }

        private void ForceQueryStoreFlush()
        {
            try
            {
                Console.WriteLine("\nFlushing Query Store to capture all queries...");
                _context.Database.ExecuteSqlCommand("EXEC sp_query_store_flush_db");
                Console.WriteLine("Query Store flush completed");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Query Store flush failed: {ex.Message}");
                Console.WriteLine("Manual flush may be needed for immediate query visibility");
            }
        }
    }
}