using System;
using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using TestEcommerceApp.Models;

namespace TestEcommerceApp.Services
{
    public class CategoryService
    {
        private readonly AppDbContext _context;

        public CategoryService(AppDbContext context)
        {
            _context = context;
        }

        // Get all categories with their product counts (many-to-many access)
        public List<Category> GetCategoriesWithProductCounts()
        {
            return _context.Category
                .Include(c => c.Products)
                .OrderBy(c => c.Name)
                .ToList();
        }

        // Get category with all its products (typical many-to-many query)
        public Category GetCategoryWithProducts(int categoryId)
        {
            return _context.Category
                .Include(c => c.Products)
                .FirstOrDefault(c => c.Id == categoryId);
        }

        // Get products by category (using many-to-many relationship)
        public List<Product> GetProductsByCategory(int categoryId)
        {
            return _context.Category
                .Where(c => c.Id == categoryId)
                .SelectMany(c => c.Products)
                .OrderBy(p => p.Name)
                .ToList();
        }

        // Cross-reference: Find products in multiple categories
        public List<Product> GetProductsInCategories(List<int> categoryIds)
        {
            return _context.Product
                .Where(p => p.Categories.Any(c => categoryIds.Contains(c.Id)))
                .Include(p => p.Categories)
                .OrderBy(p => p.Name)
                .ToList();
        }

        // Find categories that share products (complex many-to-many pattern)
        public List<Category> GetRelatedCategories(int categoryId)
        {
            var targetCategory = _context.Category
                .Include(c => c.Products)
                .FirstOrDefault(c => c.Id == categoryId);

            if (targetCategory == null) return new List<Category>();

            var productIds = targetCategory.Products.Select(p => p.Id).ToList();

            return _context.Category
                .Where(c => c.Id != categoryId && c.Products.Any(p => productIds.Contains(p.Id)))
                .Include(c => c.Products)
                .OrderBy(c => c.Name)
                .ToList();
        }

        // Popular categories based on product count
        public List<Category> GetTopCategoriesByProductCount(int count = 10)
        {
            return _context.Category
                .Include(c => c.Products)
                .OrderByDescending(c => c.Products.Count)
                .Take(count)
                .ToList();
        }

        // Get category details
        public Category GetCategory(int categoryId)
        {
            return _context.Category
                .FirstOrDefault(c => c.Id == categoryId);
        }

        // Get all categories (simple lookup)
        public List<Category> GetAllCategories()
        {
            return _context.Category
                .OrderBy(c => c.Name)
                .ToList();
        }

        // Add product to category (many-to-many modification)
        public void AddProductToCategory(int productId, int categoryId)
        {
            var product = _context.Product
                .Include(p => p.Categories)
                .FirstOrDefault(p => p.Id == productId);

            var category = _context.Category
                .FirstOrDefault(c => c.Id == categoryId);

            if (product != null && category != null && !product.Categories.Contains(category))
            {
                product.Categories.Add(category);
                _context.SaveChanges();
            }
        }

        // Remove product from category (many-to-many modification)
        public void RemoveProductFromCategory(int productId, int categoryId)
        {
            var product = _context.Product
                .Include(p => p.Categories)
                .FirstOrDefault(p => p.Id == productId);

            var category = product?.Categories.FirstOrDefault(c => c.Id == categoryId);

            if (product != null && category != null)
            {
                product.Categories.Remove(category);
                _context.SaveChanges();
            }
        }
    }
}