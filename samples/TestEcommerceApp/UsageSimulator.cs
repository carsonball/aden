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
            Console.WriteLine("Running comprehensive DynamoDB migration simulation...");
            var totalStopwatch = Stopwatch.StartNew();
            
            // Basic verification that data exists
            var customerCount = _context.Customer.Count();
            var productCount = _context.Product.Count();
            var orderCount = _context.Order.Count();
            Console.WriteLine($"Starting simulation with {customerCount} customers, {productCount} products, {orderCount} orders");
            
            if (customerCount == 0)
            {
                Console.WriteLine("No customers found - simulation cannot run");
                return;
            }

            // Run multiple denormalization candidate scenarios
            RunCustomerProfileScenario();
            RunDateBasedReportingScenario();
            RunProductCatalogScenario();
            RunOrderItemsScenario();
            
            // Force Query Store to capture all queries
            ForceQueryStoreFlush();
            
            totalStopwatch.Stop();
            Console.WriteLine($"\nTotal simulation time: {totalStopwatch.Elapsed:mm\\:ss}");
            Console.WriteLine("Query Store should now contain comprehensive usage patterns for DenormalizationCandidate analysis");
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

        private void RunDateBasedReportingScenario()
        {
            Console.WriteLine("\n=== Date-Based Reporting DynamoDB Candidate Simulation ===");
            var orderService = new OrderService(_context);
            var scenarioStopwatch = Stopwatch.StartNew();

            // HIGH FREQUENCY: Monthly reporting queries (200 calls)
            Console.WriteLine("Running monthly reporting queries (200x)...");
            var currentDate = DateTime.Now;
            for (int i = 0; i < 200; i++)
            {
                var monthsBack = _random.Next(0, 12);
                var reportDate = currentDate.AddMonths(-monthsBack);
                orderService.GetMonthlyOrders(reportDate.Year, reportDate.Month);
                
                if ((i + 1) % 40 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/200 monthly reports");
                }
                
                Thread.Sleep(_random.Next(25, 101));
            }

            // MEDIUM FREQUENCY: Date range queries (150 calls)
            Console.WriteLine("Running date range queries (150x)...");
            for (int i = 0; i < 150; i++)
            {
                var daysBack = _random.Next(7, 90);
                var startDate = currentDate.AddDays(-daysBack);
                var endDate = startDate.AddDays(_random.Next(1, 30));
                orderService.GetOrdersByDateRange(startDate, endDate);
                
                if ((i + 1) % 30 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/150 date range queries");
                }
                
                Thread.Sleep(_random.Next(30, 121));
            }

            scenarioStopwatch.Stop();
            Console.WriteLine($"\nDate-based reporting scenario completed in: {scenarioStopwatch.Elapsed:mm\\:ss}");
            Console.WriteLine("Expected Query Store results:");
            Console.WriteLine("  - Order+Customer+OrderItem queries: 350 total executions");
            Console.WriteLine("  - Date-clustered access patterns demonstrated");
            Console.WriteLine("  - Excellent DynamoDB partition key candidate (date-based)");
        }

        private void RunProductCatalogScenario()
        {
            Console.WriteLine("\n=== Product Catalog DynamoDB Candidate Simulation ===");
            var productService = new ProductService(_context);
            var scenarioStopwatch = Stopwatch.StartNew();

            // Get available categories
            var categories = productService.GetProductCategories();
            if (!categories.Any())
            {
                Console.WriteLine("No product categories found - skipping scenario");
                return;
            }

            // HIGH FREQUENCY: Category browsing (300 calls)
            Console.WriteLine("Running category browsing queries (300x)...");
            for (int i = 0; i < 300; i++)
            {
                var randomCategory = categories[_random.Next(categories.Count())];
                productService.GetProductsByCategory(randomCategory);
                
                if ((i + 1) % 50 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/300 category browses");
                }
                
                Thread.Sleep(_random.Next(20, 81));
            }

            // MEDIUM FREQUENCY: Price filtering within categories (100 calls)
            Console.WriteLine("Running category+price filtering (100x)...");
            for (int i = 0; i < 100; i++)
            {
                var randomCategory = categories[_random.Next(categories.Count())];
                var minPrice = _random.Next(10, 100);
                var maxPrice = minPrice + _random.Next(50, 500);
                productService.GetProductsByCategoryAndPriceRange(randomCategory, minPrice, maxPrice);
                
                if ((i + 1) % 20 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/100 price filters");
                }
                
                Thread.Sleep(_random.Next(35, 141));
            }

            // LOW FREQUENCY: Top products queries (75 calls)
            Console.WriteLine("Running top products queries (75x)...");
            for (int i = 0; i < 75; i++)
            {
                var randomCategory = categories[_random.Next(categories.Count())];
                var topCount = _random.Next(5, 20);
                productService.GetTopProductsByCategory(randomCategory, topCount);
                
                if ((i + 1) % 15 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/75 top product queries");
                }
                
                Thread.Sleep(_random.Next(40, 161));
            }

            scenarioStopwatch.Stop();
            Console.WriteLine($"\nProduct catalog scenario completed in: {scenarioStopwatch.Elapsed:mm\\:ss}");
            Console.WriteLine("Expected Query Store results:");
            Console.WriteLine("  - Product category+price queries: 475 total executions");
            Console.WriteLine("  - Category-based access patterns demonstrated");
            Console.WriteLine("  - Strong GSI candidate (Category as partition key)");
        }

        private void RunOrderItemsScenario()
        {
            Console.WriteLine("\n=== Order+OrderItems DynamoDB Candidate Simulation ===");
            var orderService = new OrderService(_context);
            var customerService = new CustomerService(_context);
            var scenarioStopwatch = Stopwatch.StartNew();

            var customerIds = _context.Customer.Select(c => c.Id).ToList();
            if (!customerIds.Any())
            {
                Console.WriteLine("No customers found - skipping scenario");
                return;
            }

            // HIGH FREQUENCY: Customer order history with items (250 calls)
            Console.WriteLine("Running customer order history queries (250x)...");
            for (int i = 0; i < 250; i++)
            {
                var randomCustomerId = customerIds[_random.Next(customerIds.Count)];
                orderService.GetCustomerOrderHistory(randomCustomerId);
                
                if ((i + 1) % 50 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/250 order history queries");
                }
                
                Thread.Sleep(_random.Next(30, 121));
            }

            // MEDIUM FREQUENCY: Recent customer activity (120 calls)
            Console.WriteLine("Running recent customer activity queries (120x)...");
            for (int i = 0; i < 120; i++)
            {
                var randomCustomerId = customerIds[_random.Next(customerIds.Count)];
                var daysPast = _random.Next(7, 60);
                orderService.GetRecentCustomerActivity(randomCustomerId, daysPast);
                
                if ((i + 1) % 24 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/120 recent activity queries");
                }
                
                Thread.Sleep(_random.Next(40, 161));
            }

            // MEDIUM FREQUENCY: Order details with all relationships (80 calls)
            Console.WriteLine("Running complex order detail queries (80x)...");
            var orderIds = _context.Order.Select(o => o.Id).Take(100).ToList();
            for (int i = 0; i < 80 && i < orderIds.Count; i++)
            {
                var randomOrderId = orderIds[_random.Next(orderIds.Count)];
                orderService.GetOrderWithAllDetails(randomOrderId);
                
                if ((i + 1) % 16 == 0)
                {
                    Console.WriteLine($"  Completed {i + 1}/80 complex order queries");
                }
                
                Thread.Sleep(_random.Next(50, 201));
            }

            scenarioStopwatch.Stop();
            Console.WriteLine($"\nOrder+OrderItems scenario completed in: {scenarioStopwatch.Elapsed:mm\\:ss}");
            Console.WriteLine("Expected Query Store results:");
            Console.WriteLine("  - Order+Customer+OrderItem+Product queries: 450 total executions");
            Console.WriteLine("  - Customer-centric access patterns with order items");
            Console.WriteLine("  - Strong denormalization candidate for single-table design");
        }
    }
}