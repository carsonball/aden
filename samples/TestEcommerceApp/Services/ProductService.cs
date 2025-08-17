using System;
using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using TestEcommerceApp.Models;

namespace TestEcommerceApp.Services
{
    public class ProductService
    {
        private readonly AppDbContext _context;

        public ProductService(AppDbContext context)
        {
            _context = context;
        }

        // Product catalog browsing leveraging IX_Product_Category_Price
        public List<Product> GetProductsByCategory(string category)
        {
            return _context.Product
                .Where(p => p.Category == category)
                .OrderBy(p => p.Price)
                .ToList();
        }

        // Price-based filtering within category
        public List<Product> GetProductsByCategoryAndPriceRange(string category, decimal minPrice, decimal maxPrice)
        {
            return _context.Product
                .Where(p => p.Category == category && p.Price >= minPrice && p.Price <= maxPrice)
                .OrderBy(p => p.Price)
                .ToList();
        }

        // Popular product browsing pattern
        public List<Product> GetTopProductsByCategory(string category, int count = 10)
        {
            return _context.Product
                .Where(p => p.Category == category)
                .OrderBy(p => p.Price)
                .Take(count)
                .ToList();
        }

        // Cross-category product comparison
        public List<Product> GetProductsInPriceRange(decimal minPrice, decimal maxPrice)
        {
            return _context.Product
                .Where(p => p.Price >= minPrice && p.Price <= maxPrice)
                .OrderBy(p => p.Category)
                .ThenBy(p => p.Price)
                .ToList();
        }

        // Category listing for navigation
        public List<string> GetProductCategories()
        {
            return _context.Product
                .Select(p => p.Category)
                .Distinct()
                .OrderBy(c => c)
                .ToList();
        }

        // Product details (basic lookup)
        public Product GetProduct(int productId)
        {
            return _context.Product
                .FirstOrDefault(p => p.Id == productId);
        }
    }
}