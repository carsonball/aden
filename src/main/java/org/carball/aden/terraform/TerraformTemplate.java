package org.carball.aden.terraform;

public final class TerraformTemplate {
    
    private TerraformTemplate() {
    }
    
    public static final String PROVIDER_CONFIG = """
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
        """;
    
    public static final String TABLE_RESOURCE_TEMPLATE = """
        resource "aws_dynamodb_table" "%s" {
          name           = var.%s_table_name
          billing_mode   = "PAY_PER_REQUEST"
          hash_key       = "%s"
        %s
        
        %s
        
        %s
        
          tags = merge(
            var.common_tags,
            {
              Name = var.%s_table_name
              Entity = "%s"
            }
          )
        }
        """;
    
    public static final String ATTRIBUTE_TEMPLATE = """
          attribute {
            name = "%s"
            type = "%s"
          }""";
    
    public static final String GSI_TEMPLATE = """
          global_secondary_index {
            name            = "%s"
            hash_key        = "%s"
        %s
            projection_type = "%s"
          }""";
    
    public static final String VARIABLE_TABLE_NAME_TEMPLATE = """
        variable "%s_table_name" {
          description = "Name of the DynamoDB table for %s"
          type        = string
          default     = "%s"
        }
        """;
    
    public static final String VARIABLE_COMMON_TAGS_TEMPLATE = """
        variable "common_tags" {
          description = "Common tags to apply to all resources"
          type        = map(string)
          default = {
            Environment = "production"
            ManagedBy   = "terraform"
            Application = "%s"
          }
        }
        """;
    
    public static final String OUTPUT_TABLE_NAME_TEMPLATE = """
        output "%s_table_name" {
          description = "Name of the %s DynamoDB table"
          value       = aws_dynamodb_table.%s.name
        }
        """;
    
    public static final String OUTPUT_TABLE_ARN_TEMPLATE = """
        output "%s_table_arn" {
          description = "ARN of the %s DynamoDB table"
          value       = aws_dynamodb_table.%s.arn
        }
        """;
    
    public static final String OUTPUT_TABLE_STREAM_ARN_TEMPLATE = """
        output "%s_table_stream_arn" {
          description = "Stream ARN of the %s DynamoDB table"
          value       = aws_dynamodb_table.%s.stream_arn
        }
        """;
}