using System;
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

        // Date-based queries leveraging clustered index IX_Order_OrderDate_CustomerId
        public List<Order> GetOrdersByDateRange(DateTime startDate, DateTime endDate)
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .Where(o => o.OrderDate >= startDate && o.OrderDate <= endDate)
                .OrderBy(o => o.OrderDate)
                .ThenBy(o => o.CustomerId)
                .ToList();
        }

        // Customer-centric queries leveraging IX_Order_CustomerId_OrderDate
        public List<Order> GetCustomerOrderHistory(int customerId, int maxOrders = 50)
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.Customer.Profile)
                .Include(o => o.OrderItems)
                .Where(o => o.CustomerId == customerId)
                .OrderByDescending(o => o.OrderDate)
                .Take(maxOrders)
                .ToList();
        }

        // Monthly reporting queries (common business pattern)
        public List<Order> GetMonthlyOrders(int year, int month)
        {
            var startDate = new DateTime(year, month, 1);
            var endDate = startDate.AddMonths(1).AddDays(-1);
            
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .Where(o => o.OrderDate >= startDate && o.OrderDate <= endDate)
                .OrderBy(o => o.OrderDate)
                .ToList();
        }

        // Customer service dashboard pattern
        public List<Order> GetRecentCustomerActivity(int customerId, int daysPast = 30)
        {
            var cutoffDate = DateTime.Now.AddDays(-daysPast);
            
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.Customer.Profile)
                .Include(o => o.OrderItems)
                .Where(o => o.CustomerId == customerId && o.OrderDate >= cutoffDate)
                .OrderByDescending(o => o.OrderDate)
                .ToList();
        }
    }
}