using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace TestEcommerceApp.Models
{
    public class CustomerProfile
    {
        [Key, ForeignKey("Customer")]
        public int CustomerId { get; set; }

        public string Address { get; set; }
        public string PhoneNumber { get; set; }

        public virtual Customer Customer { get; set; }
    }
}