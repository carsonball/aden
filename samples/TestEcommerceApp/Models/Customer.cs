using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;

namespace TestEcommerceApp.Models
{
    public class Customer
    {
        [Key]
        public int Id { get; set; }

        [Required]
        [MaxLength(100)]
        public string Name { get; set; }

        [MaxLength(100)]
        public string Email { get; set; }

        public DateTime CreatedDate { get; set; }

        // Navigation properties
        public virtual ICollection<Order> Orders { get; set; }
        public virtual CustomerProfile Profile { get; set; }
    }
}
