output "customerdata_table_name" {
  description = "Name of the Customer DynamoDB table"
  value       = aws_dynamodb_table.customerdata.name
}

output "customerdata_table_arn" {
  description = "ARN of the Customer DynamoDB table"
  value       = aws_dynamodb_table.customerdata.arn
}

output "applicationdata_table_name" {
  description = "Name of the CustomerProfile DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.name
}

output "applicationdata_table_arn" {
  description = "ARN of the CustomerProfile DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.arn
}

output "applicationdata_table_name" {
  description = "Name of the Order DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.name
}

output "applicationdata_table_arn" {
  description = "ARN of the Order DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.arn
}

output "applicationdata_table_name" {
  description = "Name of the OrderItem DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.name
}

output "applicationdata_table_arn" {
  description = "ARN of the OrderItem DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.arn
}

output "productdata_table_name" {
  description = "Name of the Category DynamoDB table"
  value       = aws_dynamodb_table.productdata.name
}

output "productdata_table_arn" {
  description = "ARN of the Category DynamoDB table"
  value       = aws_dynamodb_table.productdata.arn
}

output "productdata_table_name" {
  description = "Name of the Product DynamoDB table"
  value       = aws_dynamodb_table.productdata.name
}

output "productdata_table_arn" {
  description = "ARN of the Product DynamoDB table"
  value       = aws_dynamodb_table.productdata.arn
}

