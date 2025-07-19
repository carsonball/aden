using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using TestEcommerceApp.Models;

namespace TestEcommerceApp.Services
{
    public class OrderService
    {
        private readonly AppDbContext _context;

        public OrderService(AppDbContext context)
        {
            _context = context;
        }

        // Complex eager loading pattern
        public Order GetOrderWithDetails(int orderId)
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.Customer.Profile)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .FirstOrDefault(o => o.Id == orderId);
        }

        // Multiple includes pattern
        public List<Order> GetRecentOrdersForCustomer(int customerId)
        {
            return _context.Order
                .Include(o => o.OrderItems)
                .Where(o => o.CustomerId == customerId)
                .OrderByDescending(o => o.OrderDate)
                .Take(10)
                .ToList();
        }

        // Additional query patterns for higher frequency
        public List<Order> GetOrdersWithCustomerProfiles()
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.Customer.Profile)
                .ToList();
        }

        public Order GetOrderWithAllDetails(int orderId)
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.Customer.Profile)
                .Include(o => o.OrderItems)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .FirstOrDefault(o => o.Id == orderId);
        }

        public List<Order> GetOrdersWithItems()
        {
            return _context.Order
                .Include(o => o.OrderItems)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .ToList();
        }

        public List<Order> GetOrdersWithCustomers()
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.Customer.Profile)
                .ToList();
        }
    }
}