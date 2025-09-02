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
                .Where(p => p.Category != null && p.Category == category)
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
                .Where(p => p.Category != null)
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

        // String operations for product search
        public List<Product> SearchProducts(string searchTerm, string categoryPrefix)
        {
            return _context.Product
                .Where(p => p.Name.Contains(searchTerm) && 
                           p.Category.StartsWith(categoryPrefix) &&
                           p.Description.EndsWith("available"))
                .OrderBy(p => p.Price)
                .ToList();
        }

        public List<Product> SearchProductsByName(string searchTerm, string prefix, string suffix)
        {
            return _context.Product
                .Where(p => p.Name.Contains(searchTerm) &&
                           p.Category.StartsWith(prefix) &&
                           p.Description.EndsWith(suffix))
                .ToList();
        }

        public List<Product> GetProductsByNamePattern(string namePrefix)
        {
            return _context.Product
                .Where(p => p.Name.StartsWith(namePrefix))
                .OrderBy(p => p.Name)
                .ToList();
        }

        public List<Product> GetProductsByDescriptionEnding(string ending)
        {
            return _context.Product
                .Where(p => p.Description.EndsWith(ending))
                .OrderBy(p => p.Category)
                .ToList();
        }

        // Complex where clauses with value types
        public List<Product> GetProductsWithComplexFilter()
        {
            return _context.Product
                .Where(p => p.Name == "Test Product" &&
                           p.Price >= 99.99m &&
                           p.Id == 42)
                .ToList();
        }

        // Aggregation patterns
        public decimal GetAveragePrice()
        {
            return _context.Product.Average(p => p.Price);
        }

        public int GetProductCount()
        {
            return _context.Product.Count();
        }

        public decimal GetMaxPrice()
        {
            return _context.Product.Max(p => p.Price);
        }

        public decimal GetMinPrice()
        {
            return _context.Product.Min(p => p.Price);
        }

        public decimal GetTotalInventoryValue()
        {
            return _context.Product.Sum(p => p.Price);
        }

        public bool HasAnyProducts()
        {
            return _context.Product.Any();
        }

        public bool HasExpensiveProducts()
        {
            return _context.Product.Any(p => p.Price > 1000);
        }

        // GroupBy patterns with projections
        public object GetPriceStatsByCategory()
        {
            return _context.Product
                .GroupBy(p => p.Category)
                .Select(g => new { 
                    Category = g.Key, 
                    Count = g.Count(), 
                    AvgPrice = g.Average(p => p.Price),
                    MaxPrice = g.Max(p => p.Price),
                    MinPrice = g.Min(p => p.Price),
                    TotalValue = g.Sum(p => p.Price)
                })
                .OrderBy(x => x.Category)
                .ToList();
        }

        public object GetProductCountByPriceRange()
        {
            return _context.Product
                .GroupBy(p => p.Price < 100 ? "Budget" : p.Price < 500 ? "Mid" : "Premium")
                .Select(g => new { PriceRange = g.Key, Count = g.Count() })
                .ToList();
        }
    }
}