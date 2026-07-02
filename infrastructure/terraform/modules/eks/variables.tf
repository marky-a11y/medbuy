# ------------------------------------------------------------------------------
# EKS Module Variables
# ------------------------------------------------------------------------------

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "cluster_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.31"
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "private_app_subnet_ids" {
  description = "List of private-app subnet IDs for EKS worker nodes"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for ALB"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "Security group ID for EKS worker nodes"
  type        = string
}

variable "dashboard_node_group_instance_type" {
  description = "Instance type for dashboard node group"
  type        = string
  default     = "t3.medium"
}

variable "dashboard_node_group_min_size" {
  description = "Minimum number of dashboard nodes"
  type        = number
  default     = 1
}

variable "dashboard_node_group_max_size" {
  description = "Maximum number of dashboard nodes"
  type        = number
  default     = 3
}

variable "dashboard_node_group_desired_size" {
  description = "Desired number of dashboard nodes"
  type        = number
  default     = 1
}

variable "tags" {
  description = "Common tags to apply"
  type        = map(string)
  default     = {}
}
