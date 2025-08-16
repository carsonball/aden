output "customerdata_table_name" {
  description = "Name of the Customer DynamoDB table"
  value       = aws_dynamodb_table.customerdata.name
}

output "customerdata_table_arn" {
  description = "ARN of the Customer DynamoDB table"
  value       = aws_dynamodb_table.customerdata.arn
}

output "applicationdata_table_name" {
  description = "Name of the Order DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.name
}

output "applicationdata_table_arn" {
  description = "ARN of the Order DynamoDB table"
  value       = aws_dynamodb_table.applicationdata.arn
}

