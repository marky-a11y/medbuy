# ------------------------------------------------------------------------------
# Dev Environment - Main Module Instantiation
# ------------------------------------------------------------------------------

# Load shared variables
variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type    = string
  default = "dev"
}

# ----- Terraform settings -----
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
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
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


# ----- Module Instantiation -----

module "networking" {
  source = "../../modules/networking"

  environment          = var.environment
  vpc_cidr             = "10.0.0.0/16"
  availability_zones   = ["us-east-1a", "us-east-1b", "us-east-1c"]
  enable_nat_gateways  = true
  enable_vpc_endpoints = true
}

module "eks" {
  source = "../../modules/eks"

  environment                       = var.environment
  cluster_version                   = "1.31"
  vpc_id                            = module.networking.vpc_id
  private_app_subnet_ids            = module.networking.private_app_subnet_ids
  public_subnet_ids                 = module.networking.public_subnet_ids
  eks_node_security_group_id        = module.networking.eks_node_security_group_id
  dashboard_node_group_instance_type = "t3.medium"
  dashboard_node_group_min_size     = 1
  dashboard_node_group_max_size     = 3
  dashboard_node_group_desired_size = 1
}

module "rds" {
  source = "../../modules/rds"

  environment              = var.environment
  vpc_id                   = module.networking.vpc_id
  private_data_subnet_ids  = module.networking.private_data_subnet_ids
  rds_security_group_id    = module.networking.rds_security_group_id
  instance_class           = "db.t3.medium"
  multi_az                 = false
  allocated_storage        = 100
  backup_retention_days    = 7
  deletion_protection      = false
}

module "elasticache" {
  source = "../../modules/elasticache"

  environment              = var.environment
  private_data_subnet_ids  = module.networking.private_data_subnet_ids
  redis_security_group_id  = module.networking.redis_security_group_id
  node_type                = "cache.t3.micro"
  num_shards               = 1
  replicas_per_shard       = 0
  multi_az_enabled         = false
  snapshot_retention_limit = 1
}


module "security" {
  source = "../../modules/security"

  environment          = var.environment
  db_password          = module.rds.db_password == "" ? "" : module.rds.db_password
  db_username          = module.rds.db_username
  redis_auth_token     = module.elasticache.auth_token_secret_arn == "" ? "" : ""
}


# ----- Outputs -----
output "vpc_id" {
  value = module.networking.vpc_id
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  value = module.rds.db_endpoint
}

output "redis_endpoint" {
  value = module.elasticache.primary_endpoint_address
}

output "kms_key_arns" {
  value = {
    rds    = module.rds.kms_key_arn
    redis  = module.elasticache.kms_key_arn
  }
}
