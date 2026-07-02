# ------------------------------------------------------------------------------
# Shared Terraform Backend Configuration
# Remote state stored in S3 with DynamoDB locking.
# Environment-specific state key is set in environments/{env}/backend.tf
# ------------------------------------------------------------------------------

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
  }

  # Backend configuration is set per environment in:
  #   environments/dev/backend.tf
  #   environments/staging/backend.tf
  #   environments/prod/backend.tf
  backend "s3" {
    # Placeholder — values are overridden per environment
    bucket         = "media-buying-terraform-state"
    key            = "global/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "media-buying-terraform-locks"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "MediaBuyingDashboard"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Owner       = "DevOps"
    }
  }
}
