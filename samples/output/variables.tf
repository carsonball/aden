variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Environment = "production"
    ManagedBy   = "terraform"
    Application = "TestEcommerceApp"
  }
}

variable "customerdata_table_name" {
  description = "Name of the DynamoDB table for Customer"
  type        = string
  default     = "CustomerData"
}

variable "applicationdata_table_name" {
  description = "Name of the DynamoDB table for Order"
  type        = string
  default     = "ApplicationData"
}

