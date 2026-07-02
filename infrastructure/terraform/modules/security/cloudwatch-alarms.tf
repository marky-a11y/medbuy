# ------------------------------------------------------------------------------
# CloudWatch Alarms for Monitoring (INFRA-11)
# Alarms for RDS, ElastiCache, ALB, and EKS metrics.
# ------------------------------------------------------------------------------

locals {
  alarm_actions = var.environment == "prod" ? [
    # Replace with actual SNS topic ARN for PagerDuty integration
    "arn:aws:sns:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:media-buying-${var.environment}-critical"
  ] : []

  warning_actions = var.environment == "prod" ? [
    "arn:aws:sns:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:media-buying-${var.environment}-warning"
  ] : []
}

data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

# ---- RDS Alarms ----
resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "media-buying-${var.environment}-rds-cpu-high"
  alarm_description   = "RDS CPU utilization exceeds 80% for 5 minutes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
  insufficient_data_actions = []

  dimensions = {
    DBInstanceIdentifier = "media-buying-${var.environment}-db"
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage_low" {
  alarm_name          = "media-buying-${var.environment}-rds-free-storage-low"
  alarm_description   = "RDS free storage space is below 10 GB"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = "300"
  statistic           = "Average"
  threshold           = "10737418240"  # 10 GB in bytes
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    DBInstanceIdentifier = "media-buying-${var.environment}-db"
  }
}

resource "aws_cloudwatch_metric_alarm" "rds_connections_high" {
  alarm_name          = "media-buying-${var.environment}-rds-connections-high"
  alarm_description   = "RDS database connections exceed 80% of max"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = "300"
  statistic           = "Average"
  threshold           = "160"  # 80% of max_connections=200
  alarm_actions       = local.warning_actions

  dimensions = {
    DBInstanceIdentifier = "media-buying-${var.environment}-db"
  }
}

# ---- ElastiCache Alarms ----
resource "aws_cloudwatch_metric_alarm" "redis_cpu_high" {
  alarm_name          = "media-buying-${var.environment}-redis-cpu-high"
  alarm_description   = "Redis CPU utilization exceeds 80% for 5 minutes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ElastiCache"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_actions       = local.alarm_actions

  dimensions = {
    CacheClusterId = "media-buying-${var.environment}-redis-001"
  }
}

resource "aws_cloudwatch_metric_alarm" "redis_evictions_high" {
  alarm_name          = "media-buying-${var.environment}-redis-evictions-high"
  alarm_description   = "Redis evictions rate is high, indicating memory pressure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "Evictions"
  namespace           = "AWS/ElastiCache"
  period              = "300"
  statistic           = "Sum"
  threshold           = "100"
  alarm_actions       = local.warning_actions

  dimensions = {
    CacheClusterId = "media-buying-${var.environment}-redis-001"
  }
}

# ---- ALB Alarms ----
resource "aws_cloudwatch_metric_alarm" "alb_target_response_time_high" {
  alarm_name          = "media-buying-${var.environment}-alb-target-response-time-high"
  alarm_description   = "ALB target response time exceeds 2 seconds for 5 minutes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Average"
  threshold           = "2"
  alarm_actions       = local.warning_actions

  dimensions = {
    LoadBalancer = ""
  }
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx_high" {
  alarm_name          = "media-buying-${var.environment}-alb-5xx-high"
  alarm_description   = "ALB 5XX error rate exceeds 1%"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"  # More than 10 5xx errors in 5 minutes
  alarm_actions       = local.alarm_actions

  dimensions = {
    LoadBalancer = ""
  }
}
