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
            return _context.Customer
                .Include(c => c.Orders)
                .Include(c => c.Profile)
                .FirstOrDefault(c => c.Id == customerId);
        }

        // Complex query pattern
        public List<Order> GetCustomerOrdersWithItems(int customerId)
        {
            return _context.Order
                .Include(o => o.OrderItems)
                .Include(o => o.OrderItems.Select(oi => oi.Product))
                .Where(o => o.CustomerId == customerId)
                .ToList();
        }

        // More eager loading patterns to increase frequency
        public Customer GetCustomerWithProfile(int customerId)
        {
            return _context.Customer
                .Include(c => c.Profile)
                .FirstOrDefault(c => c.Id == customerId);
        }

        public Customer GetCustomerWithOrderHistory(int customerId)
        {
            return _context.Customer
                .Include(c => c.Orders)
                .Include(c => c.Profile)
                .FirstOrDefault(c => c.Id == customerId);
        }

        public List<Customer> GetCustomersWithProfiles()
        {
            return _context.Customer
                .Include(c => c.Profile)
                .ToList();
        }

        public List<Customer> GetCustomersWithProfiles(int batchSize)
        {
            return _context.Customer
                .Include(c => c.Profile)
                .Take(batchSize)
                .ToList();
        }

        public List<Customer> GetActiveCustomersWithOrders()
        {
            return _context.Customer
                .Include(c => c.Orders)
                .Include(c => c.Profile)
                .Where(c => c.Orders.Any())
                .ToList();
        }

        public Customer GetCustomerFullData(int customerId)
        {
            return _context.Customer
                .Include(c => c.Profile)
                .Include(c => c.Orders)
                .Include(c => c.Orders.Select(o => o.OrderItems))
                .FirstOrDefault(c => c.Id == customerId);
        }

        public void UpdateCustomerProfile(int customerId, string address = null, string phoneNumber = null)
        {
            var customer = _context.Customer
                .Include(c => c.Profile)
                .FirstOrDefault(c => c.Id == customerId);

            if (customer?.Profile != null)
            {
                if (address != null)
                    customer.Profile.Address = address;
                if (phoneNumber != null)
                    customer.Profile.PhoneNumber = phoneNumber;

                _context.SaveChanges();
            }
        }

        // String operations for customer search
        public List<Customer> SearchCustomersByName(string searchTerm)
        {
            return _context.Customer
                .Where(c => c.FirstName.Contains(searchTerm) || c.LastName.StartsWith(searchTerm))
                .Include(c => c.Profile)
                .ToList();
        }

        public List<Customer> GetCustomersByNamePrefix(string namePrefix)
        {
            return _context.Customer
                .Where(c => c.FirstName.StartsWith(namePrefix))
                .Include(c => c.Profile)
                .ToList();
        }

        public List<Customer> GetCustomersByEmailDomain(string domain)
        {
            return _context.Customer
                .Where(c => c.Email.EndsWith(domain))
                .Include(c => c.Profile)
                .ToList();
        }

        // Complex where clauses with multiple conditions
        public List<Customer> GetVIPCustomersInRegion(string region, DateTime since)
        {
            return _context.Customer
                .Include(c => c.Profile)
                .Include(c => c.Orders)
                .Where(c => (c.Profile.Address.Contains(region) && c.CreatedDate >= since) ||
                           (c.Orders.Any() && c.Orders.Sum(o => o.TotalAmount) > 1000))
                .ToList();
        }

        public List<Customer> GetActiveCustomersByDateAndStatus(DateTime startDate, DateTime endDate, string status)
        {
            return _context.Customer
                .Include(c => c.Profile)
                .Include(c => c.Orders)
                .Where(c => (c.CreatedDate >= startDate && c.CreatedDate <= endDate) ||
                           (c.Email.Contains(status) && c.Orders.Count() > 5) ||
                           c.Profile.Address.StartsWith("Premium"))
                .ToList();
        }
    }
}