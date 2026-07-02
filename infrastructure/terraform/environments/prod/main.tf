# ------------------------------------------------------------------------------
# Production Environment - Main Module Instantiation
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
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
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

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type    = string
  default = "prod"
}

module "networking" {
  source = "../../modules/networking"

  environment          = var.environment
  vpc_cidr             = var.vpc_cidr
  availability_zones   = var.availability_zones
  enable_nat_gateways  = var.enable_nat_gateways
  enable_vpc_endpoints = var.enable_vpc_endpoints
}

module "eks" {
  source = "../../modules/eks"

  environment                       = var.environment
  cluster_version                   = var.cluster_version
  vpc_id                            = module.networking.vpc_id
  private_app_subnet_ids            = module.networking.private_app_subnet_ids
  public_subnet_ids                 = module.networking.public_subnet_ids
  eks_node_security_group_id        = module.networking.eks_node_security_group_id
  dashboard_node_group_instance_type = var.dashboard_node_group_instance_type
  dashboard_node_group_min_size     = var.dashboard_node_group_min_size
  dashboard_node_group_max_size     = var.dashboard_node_group_max_size
  dashboard_node_group_desired_size = var.dashboard_node_group_desired_size
}

module "rds" {
  source = "../../modules/rds"

  environment              = var.environment
  vpc_id                   = module.networking.vpc_id
  private_data_subnet_ids  = module.networking.private_data_subnet_ids
  rds_security_group_id    = module.networking.rds_security_group_id
  instance_class           = var.rds_instance_class
  multi_az                 = var.rds_multi_az
  allocated_storage        = var.rds_allocated_storage
  backup_retention_days    = var.rds_backup_retention
  deletion_protection      = var.rds_deletion_protection
}

module "elasticache" {
  source = "../../modules/elasticache"

  environment              = var.environment
  private_data_subnet_ids  = module.networking.private_data_subnet_ids
  redis_security_group_id  = module.networking.redis_security_group_id
  node_type                = var.redis_node_type
  num_shards               = var.redis_num_shards
  replicas_per_shard       = var.redis_replicas_per_shard
  multi_az_enabled         = var.redis_multi_az
  snapshot_retention_limit = var.redis_snapshot_retention
}


module "security" {
  source = "../../modules/security"

  environment          = var.environment
  db_password          = module.rds.db_password
  db_username          = module.rds.db_username
}

# Variables (populated from terraform.tfvars)
variable "vpc_cidr" { type = string }
variable "availability_zones" { type = list(string) }
variable "enable_nat_gateways" { type = bool }
variable "enable_vpc_endpoints" { type = bool }
variable "cluster_version" { type = string }
variable "dashboard_node_group_instance_type" { type = string }
variable "dashboard_node_group_min_size" { type = number }
variable "dashboard_node_group_max_size" { type = number }
variable "dashboard_node_group_desired_size" { type = number }
variable "rds_instance_class" { type = string }
variable "rds_multi_az" { type = bool }
variable "rds_allocated_storage" { type = number }
variable "rds_backup_retention" { type = number }
variable "rds_deletion_protection" { type = bool }
variable "redis_node_type" { type = string }
variable "redis_num_shards" { type = number }
variable "redis_replicas_per_shard" { type = number }
variable "redis_multi_az" { type = bool }
variable "redis_snapshot_retention" { type = number }

