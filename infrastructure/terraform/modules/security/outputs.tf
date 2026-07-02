# ------------------------------------------------------------------------------
# Security Module Outputs
# ------------------------------------------------------------------------------

output "db_credentials_secret_arn" {
  description = "ARN of the DB credentials secret"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "db_credentials_secret_name" {
  description = "Name of the DB credentials secret"
  value       = aws_secretsmanager_secret.db_credentials.name
}

output "redis_auth_secret_arn" {
  description = "ARN of the Redis AUTH secret"
  value       = aws_secretsmanager_secret.redis_auth.arn
}

output "api_keys_secret_arn" {
  description = "ARN of the API keys secret"
  value       = aws_secretsmanager_secret.api_keys.arn
}

output "rotation_lambda_arn" {
  description = "ARN of the DB credentials rotation Lambda"
  value       = try(aws_lambda_function.db_rotation[0].arn, null)
}
