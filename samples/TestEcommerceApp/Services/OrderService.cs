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

        // Explicit aggregation methods
        public decimal GetTotalRevenue()
        {
            return _context.Order.Sum(o => o.TotalAmount);
        }

        public int GetOrderCount()
        {
            return _context.Order.Count();
        }

        public decimal GetAverageOrderValue()
        {
            return _context.Order.Average(o => o.TotalAmount);
        }

        public bool HasRecentOrders()
        {
            return _context.Order.Any(o => o.OrderDate >= DateTime.Now.AddDays(-30));
        }

        public bool HasAnyOrders()
        {
            return _context.Order.Any();
        }

        public decimal GetRevenueForDateRange(DateTime startDate, DateTime endDate)
        {
            return _context.Order
                .Where(o => o.OrderDate >= startDate && o.OrderDate <= endDate)
                .Sum(o => o.TotalAmount);
        }

        // GroupBy patterns with projections
        public var GetOrderStatsByStatus()
        {
            return _context.Order
                .GroupBy(o => o.Status)
                .Select(g => new { Status = g.Key, Count = g.Count(), Total = g.Sum(o => o.TotalAmount) })
                .ToList();
        }

        public var GetOrderStatsByCustomer()
        {
            return _context.Order
                .GroupBy(o => o.CustomerId)
                .Select(g => new { 
                    CustomerId = g.Key, 
                    OrderCount = g.Count(), 
                    TotalSpent = g.Sum(o => o.TotalAmount),
                    AverageOrder = g.Average(o => o.TotalAmount)
                })
                .ToList();
        }

        public var GetMonthlyOrderSummary()
        {
            return _context.Order
                .GroupBy(o => new { o.OrderDate.Year, o.OrderDate.Month })
                .Select(g => new { 
                    Year = g.Key.Year,
                    Month = g.Key.Month,
                    Count = g.Count(),
                    Revenue = g.Sum(o => o.TotalAmount)
                })
                .OrderBy(x => x.Year)
                .ThenBy(x => x.Month)
                .ToList();
        }

        // Full pagination with Skip/Take
        public List<Order> GetOrdersPage(int page, int pageSize)
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.OrderItems)
                .OrderBy(o => o.OrderDate)
                .Skip(page * pageSize)
                .Take(pageSize)
                .ToList();
        }

        public List<Order> GetOrdersPageByCustomer(int customerId, int page, int pageSize)
        {
            return _context.Order
                .Include(o => o.Customer)
                .Include(o => o.OrderItems)
                .Where(o => o.CustomerId == customerId)
                .OrderByDescending(o => o.OrderDate)
                .Skip(page * pageSize)
                .Take(pageSize)
                .ToList();
        }
    }
}