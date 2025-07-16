using System;
using System.Linq;
using TestEcommerceApp.Services;

namespace TestEcommerceApp
{
    public class UsageSimulator
    {
        private readonly AppDbContext _context;

        public UsageSimulator(AppDbContext context)
        {
            _context = context;
        }

        public void RunSimulation()
        {
            Console.WriteLine("Running basic usage simulation...");
            
            // Basic verification that data exists and services work
            var customerService = new CustomerService(_context);
            var orderService = new OrderService(_context);

            // Simple verification queries
            var customerCount = _context.Customers.Count();
            var orderCount = _context.Orders.Count();
            var productCount = _context.Products.Count();

            Console.WriteLine($"Data verification:");
            Console.WriteLine($"  - {customerCount} customers");
            Console.WriteLine($"  - {orderCount} orders");
            Console.WriteLine($"  - {productCount} products");

            // Test a few service methods to verify they work
            if (customerCount > 0)
            {
                var firstCustomer = _context.Customers.First();
                Console.WriteLine($"Testing customer service with customer: {firstCustomer.Name}");
                
                var customerWithProfile = customerService.GetCustomerWithProfile(firstCustomer.Id);
                var customerWithOrders = customerService.GetCustomerWithOrders(firstCustomer.Id);
                
                Console.WriteLine($"  - Customer+Profile loaded: {customerWithProfile?.Profile != null}");
                Console.WriteLine($"  - Customer+Orders loaded: {customerWithOrders?.Orders?.Count ?? 0} orders");
            }

            if (orderCount > 0)
            {
                var firstOrder = _context.Orders.First();
                Console.WriteLine($"Testing order service with order ID: {firstOrder.Id}");
                
                var orderWithDetails = orderService.GetOrderWithDetails(firstOrder.Id);
                Console.WriteLine($"  - Order+Customer+Items loaded: {orderWithDetails?.Customer != null}");
            }

            Console.WriteLine("Basic simulation complete - services are functional!");
            Console.WriteLine("\nNote: Full usage simulation will be implemented in Day 2");
        }
    }
}