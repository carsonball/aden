using System;
using System.Linq;
using TestEcommerceApp.Models;

namespace TestEcommerceApp
{
    public class TestDataSeeder
    {
        private readonly AppDbContext _context;

        public TestDataSeeder(AppDbContext context)
        {
            _context = context;
        }

        public void SeedData()
        {
            Console.WriteLine("Seeding test data...");

            // Create products (low priority migration candidates)
            var products = new[]
            {
                new Product { Name = "Laptop", Price = 1200.00m, Description = "High-performance laptop" },
                new Product { Name = "Mouse", Price = 25.00m, Description = "Wireless mouse" },
                new Product { Name = "Keyboard", Price = 75.00m, Description = "Mechanical keyboard" },
                new Product { Name = "Monitor", Price = 300.00m, Description = "4K monitor" },
                new Product { Name = "Headphones", Price = 150.00m, Description = "Noise-canceling headphones" },
                new Product { Name = "Webcam", Price = 100.00m, Description = "HD webcam" },
                new Product { Name = "Tablet", Price = 500.00m, Description = "10-inch tablet" },
                new Product { Name = "Phone", Price = 800.00m, Description = "Smartphone" }
            };

            _context.Products.AddRange(products);
            _context.SaveChanges();

            // Create customers with profiles (high priority DynamoDB candidates)
            var customers = new[]
            {
                new Customer 
                { 
                    Name = "John Smith", 
                    Email = "john.smith@email.com",
                    CreatedDate = DateTime.Now,
                    Profile = new CustomerProfile 
                    { 
                        Address = "123 Main St", 
                        PhoneNumber = "555-0101"
                    }
                },
                new Customer 
                { 
                    Name = "Jane Doe", 
                    Email = "jane.doe@email.com",
                    CreatedDate = DateTime.Now,
                    Profile = new CustomerProfile 
                    { 
                        Address = "456 Oak Ave", 
                        PhoneNumber = "555-0102"
                    }
                },
                new Customer 
                { 
                    Name = "Bob Johnson", 
                    Email = "bob.johnson@email.com",
                    CreatedDate = DateTime.Now,
                    Profile = new CustomerProfile 
                    { 
                        Address = "789 Pine St", 
                        PhoneNumber = "555-0103"
                    }
                },
                new Customer 
                { 
                    Name = "Alice Wilson", 
                    Email = "alice.wilson@email.com",
                    CreatedDate = DateTime.Now,
                    Profile = new CustomerProfile 
                    { 
                        Address = "321 Elm Dr", 
                        PhoneNumber = "555-0104"
                    }
                },
                new Customer 
                { 
                    Name = "Charlie Brown", 
                    Email = "charlie.brown@email.com",
                    CreatedDate = DateTime.Now,
                    Profile = new CustomerProfile 
                    { 
                        Address = "654 Maple Ln", 
                        PhoneNumber = "555-0105"
                    }
                }
            };

            _context.Customers.AddRange(customers);
            _context.SaveChanges();

            // Create orders with items (medium priority DocumentDB candidates)
            var random = new Random(42); // Fixed seed for consistent data

            foreach (var customer in customers)
            {
                // Each customer gets 2-4 orders
                var orderCount = random.Next(2, 5);
                
                for (int i = 0; i < orderCount; i++)
                {
                    var order = new Order
                    {
                        CustomerId = customer.Id,
                        OrderDate = DateTime.Now.AddDays(-random.Next(1, 365)),
                        TotalAmount = 0 // Will be calculated after adding items
                    };

                    _context.Orders.Add(order);
                    _context.SaveChanges(); // Save to get order ID

                    // Each order gets 1-4 items
                    var itemCount = random.Next(1, 5);
                    decimal orderTotal = 0;

                    for (int j = 0; j < itemCount; j++)
                    {
                        var product = products[random.Next(products.Length)];
                        var quantity = random.Next(1, 4);

                        var orderItem = new OrderItem
                        {
                            OrderId = order.Id,
                            ProductId = product.Id,
                            Quantity = quantity,
                            Price = product.Price
                        };

                        _context.OrderItems.Add(orderItem);
                        orderTotal += product.Price * quantity;
                    }

                    // Update order total
                    order.TotalAmount = orderTotal;
                    _context.SaveChanges();
                }
            }

            Console.WriteLine($"Seeded {products.Length} products, {customers.Length} customers, and {_context.Orders.Count()} orders.");
        }
    }
}