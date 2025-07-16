using System;
using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using TestEcommerceApp.Models;

namespace TestEcommerceApp
{
    public class AppDbContext : DbContext
    {
        public AppDbContext() : base("DefaultConnection")
        {
        }

        public DbSet<Customer> Customers { get; set; }
        public DbSet<Order> Orders { get; set; }
        public DbSet<OrderItem> OrderItems { get; set; }
        public DbSet<Product> Products { get; set; }
        public DbSet<CustomerProfile> CustomerProfiles { get; set; }
    }
}
