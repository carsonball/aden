using System;
using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using TestEcommerceApp.Models;

namespace TestEcommerceApp.Services
{
    public class CustomerService
    {
        private readonly AppDbContext _context;

        public CustomerService(AppDbContext context)
        {
            _context = context;
        }

        // Classic eager loading pattern
        public Customer GetCustomerWithOrders(int customerId)
        {
            return _context.Customers
                .Include(c => c.Orders)
                .Include(c => c.Profile)
                .FirstOrDefault(c => c.Id == customerId);
        }

        // Complex query pattern
        public List<Order> GetCustomerOrdersWithItems(int customerId)
        {
            return _context.Orders
                .Include(o => o.OrderItems)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .Where(o => o.CustomerId == customerId)
                .ToList();
        }
    }
}