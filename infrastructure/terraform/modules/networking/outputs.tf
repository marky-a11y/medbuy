# ------------------------------------------------------------------------------
# Networking Module Outputs
# ------------------------------------------------------------------------------

output "vpc_id" {
  description = "The ID of the VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr_block" {
  description = "The CIDR block of the VPC"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = aws_subnet.public[*].id
}

output "private_app_subnet_ids" {
  description = "IDs of the private-app subnets (EKS worker nodes)"
  value       = aws_subnet.private_app[*].id
}

output "private_data_subnet_ids" {
  description = "IDs of the private-data subnets (RDS, ElastiCache)"
  value       = aws_subnet.private_data[*].id
}

output "nat_gateway_ids" {
  description = "IDs of the NAT Gateways"
  value       = aws_nat_gateway.main[*].id
}

output "alb_security_group_id" {
  description = "Security group ID for the ALB"
  value       = aws_security_group.alb.id
}

output "eks_node_security_group_id" {
  description = "Security group ID for EKS worker nodes"
  value       = aws_security_group.eks_node.id
}

output "rds_security_group_id" {
  description = "Security group ID for RDS PostgreSQL"
  value       = aws_security_group.rds.id
}

output "redis_security_group_id" {
  description = "Security group ID for ElastiCache Redis"
  value       = aws_security_group.redis.id
}

output "private_route_table_ids" {
  description = "IDs of the private route tables"
  value       = aws_route_table.private_app[*].id
}

output "vpc_endpoint_s3_id" {
  description = "ID of the S3 VPC endpoint"
  value       = try(aws_vpc_endpoint.s3[0].id, null)
}

output "vpc_endpoint_ecr_api_id" {
  description = "ID of the ECR API VPC endpoint"
  value       = try(aws_vpc_endpoint.ecr_api[0].id, null)
}

output "vpc_endpoint_ecr_dkr_id" {
  description = "ID of the ECR DKR VPC endpoint"
  value       = try(aws_vpc_endpoint.ecr_dkr[0].id, null)
}

output "vpc_endpoint_secrets_manager_id" {
  description = "ID of the Secrets Manager VPC endpoint"
  value       = try(aws_vpc_endpoint.secrets_manager[0].id, null)
}

output "vpc_endpoint_cloudwatch_logs_id" {
  description = "ID of the CloudWatch Logs VPC endpoint"
  value       = try(aws_vpc_endpoint.cloudwatch_logs[0].id, null)
}

output "vpc_endpoint_sts_id" {
  description = "ID of the STS VPC endpoint"
  value       = try(aws_vpc_endpoint.sts[0].id, null)
}
