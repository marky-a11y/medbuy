# ------------------------------------------------------------------------------
# RDS PostgreSQL Module
# Provisions Multi-AZ RDS PostgreSQL with KMS encryption and Enhanced Monitoring.
# ------------------------------------------------------------------------------

# ---------------
# KMS Key for RDS Encryption
# ---------------
resource "aws_kms_key" "rds" {
  description             = "KMS key for RDS PostgreSQL encryption - ${var.environment}"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-rds-kms"
  })
}

resource "aws_kms_alias" "rds" {
  name          = "alias/media-buying-${var.environment}-rds"
  target_key_id = aws_kms_key.rds.key_id
}

# ---------------
# DB Subnet Group
# ---------------
resource "aws_db_subnet_group" "main" {
  name        = "media-buying-${var.environment}-db-subnet-group"
  description = "DB subnet group for media-buying ${var.environment}"
  subnet_ids  = var.private_data_subnet_ids

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-db-subnet-group"
  })
}

# ---------------
# DB Parameter Group
# ---------------
resource "aws_db_parameter_group" "main" {
  name        = "media-buying-${var.environment}-pg-15"
  family      = "postgres15"
  description = "Custom parameter group for PostgreSQL 15 - ${var.environment}"

  parameter {
    name  = "max_connections"
    value = "200"
  }

  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory*3/4}"
    apply_method = "pending-reboot"
  }

  parameter {
    name  = "work_mem"
    value = "4096"
  }

  parameter {
    name  = "maintenance_work_mem"
    value = "65536"
  }

  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory*3/4}"
    apply_method = "pending-reboot"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  parameter {
    name  = "idle_in_transaction_session_timeout"
    value = "300000"
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-pg-15-params"
  })
}

# ---------------
# DB Option Group
# ---------------
resource "aws_db_option_group" "main" {
  name                 = "media-buying-${var.environment}-og-15"
  engine_name          = "postgres"
  major_engine_version = "15"

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-og-15"
  })
}

# ---------------
# RDS PostgreSQL Instance
# ---------------
resource "aws_db_instance" "main" {
  identifier = "media-buying-${var.environment}-db"

  engine         = "postgres"
  engine_version = "15.8"
  engine_version_auto_upgrade = false

  instance_class        = var.instance_class
  multi_az              = var.multi_az
  db_name               = var.db_name
  username              = var.db_username
  password              = random_password.db_password.result
  port                  = 5432

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  iops                  = var.storage_iops
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.rds.arn

  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.main.name
  option_group_name      = aws_db_option_group.main.name
  vpc_security_group_ids = [var.rds_security_group_id]

  backup_retention_period = var.backup_retention_days
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:05:00-sun:06:00"
  copy_tags_to_snapshot  = true
  delete_automated_backups = true
  skip_final_snapshot     = false
  final_snapshot_identifier = "media-buying-${var.environment}-db-final-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
  deletion_protection    = var.deletion_protection

  auto_minor_version_upgrade = var.environment == "dev" ? true : false

  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  enabled_cloudwatch_logs_exports       = ["postgresql"]

  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_enhanced_monitoring.arn

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-db"
  })

  lifecycle {
    ignore_changes = [
      final_snapshot_identifier,
      latest_restorable_time
    ]
  }
}

# ---------------
# Random Password for DB
# ---------------
resource "random_password" "db_password" {
  length  = 32
  special = false
  keepers = {
    environment = var.environment
    db_name     = var.db_name
  }
}

# ---------------
# IAM Role for Enhanced Monitoring
# ---------------
resource "aws_iam_role" "rds_enhanced_monitoring" {
  name = "media-buying-${var.environment}-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
  role       = aws_iam_role.rds_enhanced_monitoring.name
}
