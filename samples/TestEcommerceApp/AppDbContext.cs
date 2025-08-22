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
            // Disable initializer (very common in legacy code)
            Database.SetInitializer<AppDbContext>(null);
        }

        public DbSet<Customer> Customer { get; set; }
        public DbSet<Order> Order { get; set; }
        public DbSet<OrderItem> OrderItem { get; set; }
        public DbSet<Product> Product { get; set; }
        public DbSet<CustomerProfile> CustomerProfile { get; set; }
        public DbSet<Category> Category { get; set; }

        protected override void OnModelCreating(DbModelBuilder modelBuilder)
        {
            // Explicitly map to singular table names to match our Docker schema
            modelBuilder.Entity<Customer>().ToTable("Customer");
            modelBuilder.Entity<Order>().ToTable("Order");
            modelBuilder.Entity<OrderItem>().ToTable("OrderItem");
            modelBuilder.Entity<Product>().ToTable("Product");
            modelBuilder.Entity<CustomerProfile>().ToTable("CustomerProfile");
            modelBuilder.Entity<Category>().ToTable("Category");

            // Configure many-to-many relationship between Product and Category
            modelBuilder.Entity<Product>()
                .HasMany(p => p.Categories)
                .WithMany(c => c.Products)
                .Map(m =>
                {
                    m.ToTable("ProductCategory");
                    m.MapLeftKey("ProductId");
                    m.MapRightKey("CategoryId");
                });
            
            base.OnModelCreating(modelBuilder);
        }
    }
}
