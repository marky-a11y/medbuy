# ------------------------------------------------------------------------------
# ElastiCache Module Outputs
# ------------------------------------------------------------------------------

output "replication_group_id" {
  description = "Replication group ID"
  value       = aws_elasticache_replication_group.main.id
}

output "replication_group_arn" {
  description = "Replication group ARN"
  value       = aws_elasticache_replication_group.main.arn
}

output "primary_endpoint_address" {
  description = "Primary endpoint address"
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "primary_endpoint_port" {
  description = "Primary endpoint port"
  value       = aws_elasticache_replication_group.main.port
}

output "reader_endpoint_address" {
  description = "Reader endpoint address (for replica reads)"
  value       = aws_elasticache_replication_group.main.reader_endpoint_address
}

output "parameter_group_name" {
  description = "Parameter group name"
  value       = aws_elasticache_parameter_group.main.name
}

output "kms_key_arn" {
  description = "KMS key ARN for Redis encryption"
  value       = aws_kms_key.redis.arn
}

output "auth_token_secret_arn" {
  description = "ARN of the Secrets Manager entry for the AUTH token"
  value       = aws_secretsmanager_secret.redis_auth.arn
}
