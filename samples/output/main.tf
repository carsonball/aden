terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  # Configure your AWS region
  # region = "us-east-1"
}

resource "aws_dynamodb_table" "customerdata" {
  name         = var.customerdata_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "customerId"
  range_key    = "entityType#timestamp"

  attribute {
    name = "customerId"
    type = "S"
  }
  attribute {
    name = "entityType#timestamp"
    type = "S"
  }
  attribute {
    name = "email"
    type = "S"
  }


  global_secondary_index {
    name            = "EmailIndex"
    hash_key        = "email"
    range_key       = "customerId"
    projection_type = "ALL"
  }

  tags = merge(
    var.common_tags,
    {
      Name   = var.customerdata_table_name
      Entity = "Customer"
    }
  )
}

resource "aws_dynamodb_table" "applicationdata" {
  name         = var.applicationdata_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "customerId"
  range_key    = "entityType#orderId"

  attribute {
    name = "customerId"
    type = "S"
  }
  attribute {
    name = "entityType#orderId"
    type = "S"
  }




  tags = merge(
    var.common_tags,
    {
      Name   = var.applicationdata_table_name
      Entity = "Order"
    }
  )
}

