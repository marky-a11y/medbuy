# ------------------------------------------------------------------------------
# ElastiCache Redis Module
# Provisions Redis Cluster Mode with encryption, TLS, and AUTH token.
# ------------------------------------------------------------------------------

# ---------------
# KMS Key for Redis Encryption at Rest
# ---------------
resource "aws_kms_key" "redis" {
  description             = "KMS key for ElastiCache Redis encryption - ${var.environment}"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis-kms"
  })
}

resource "aws_kms_alias" "redis" {
  name          = "alias/media-buying-${var.environment}-redis"
  target_key_id = aws_kms_key.redis.key_id
}

# ---------------
# Redis Parameter Group
# ---------------
resource "aws_elasticache_parameter_group" "main" {
  name        = "media-buying-${var.environment}-redis7-params"
  family      = "redis7"

  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"
  }

  parameter {
    name  = "timeout"
    value = "300"
  }

  parameter {
    name  = "tls-auth-clients"
    value = "yes"
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis7-params"
  })
}

# ---------------
# Redis Subnet Group
# ---------------
resource "aws_elasticache_subnet_group" "main" {
  name        = "media-buying-${var.environment}-redis-subnet-group"
  description = "Redis subnet group for ${var.environment}"
  subnet_ids  = var.private_data_subnet_ids

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis-subnet-group"
  })
}

# ---------------
# AUTH Token in Secrets Manager
# ---------------
resource "random_password" "redis_auth" {
  length  = 32
  special = false
  keepers = {
    environment = var.environment
  }
}

resource "aws_secretsmanager_secret" "redis_auth" {
  name        = "media-buying/${var.environment}/redis-auth"
  description = "Redis AUTH token for media-buying ${var.environment}"
  kms_key_id  = aws_kms_key.redis.arn

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis-auth"
  })
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id = aws_secretsmanager_secret.redis_auth.id
  secret_string = random_password.redis_auth.result
}

# ---------------
# Redis Replication Group (Cluster Mode)
# ---------------
resource "aws_elasticache_replication_group" "main" {
  replication_group_id          = "media-buying-${var.environment}-redis"
  replication_group_description = "Redis cluster for media-buying ${var.environment}"
  node_type                     = var.node_type
  port                          = 6379

  engine         = "redis"
  engine_version = "7.1"
  parameter_group_name = aws_elasticache_parameter_group.main.name
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [var.redis_security_group_id]

  # Cluster Mode
  cluster_mode_enabled = true
  num_node_groups      = var.num_shards
  replicas_per_node_group = var.replicas_per_shard

  # Multi-AZ
  automatic_failover_enabled = var.multi_az_enabled
  multi_az_enabled           = var.multi_az_enabled && length(var.private_data_subnet_ids) >= 2

  # Encryption
  at_rest_encryption_enabled = true
  kms_key_id                = aws_kms_key.redis.arn
  transit_encryption_enabled = true

  # Auth
  auth_token = random_password.redis_auth.result

  # Snapshot
  snapshot_retention_limit = var.snapshot_retention_limit
  snapshot_window          = "04:00-05:00"
  maintenance_window       = "sun:06:00-sun:07:00"

  auto_minor_version_upgrade = var.environment == "dev" ? true : false

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis"
  })

  lifecycle {
    ignore_changes = [
      engine_version
    ]
  }
}
