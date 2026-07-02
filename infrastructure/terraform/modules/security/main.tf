# ------------------------------------------------------------------------------
# Security Module
# Secrets Manager, Parameter Store, and DB credential rotation Lambda.
# ------------------------------------------------------------------------------

# ---------------
# KMS Key for Secrets Manager
# ---------------
resource "aws_kms_key" "secrets" {
  description             = "KMS key for Secrets Manager - ${var.environment}"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-secrets-kms"
  })
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/media-buying-${var.environment}-secrets"
  target_key_id = aws_kms_key.secrets.key_id
}

# ==============================================================================
# SECRETS MANAGER
# ==============================================================================

# ---- 1. DB Credentials ----
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "media-buying/${var.environment}/db-credentials"
  description = "PostgreSQL database credentials for media-buying ${var.environment}"
  kms_key_id  = aws_kms_key.secrets.arn

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-db-credentials"
  })
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.db_username
    password = var.db_password
    engine   = "postgres"
    host     = "" # Populated after RDS creation via ESO or at runtime
    port     = 5432
    dbname   = "media_buying"
    dbInstanceIdentifier = "media-buying-${var.environment}-db"
  })
}

# ---- 2. Redis AUTH ----
resource "aws_secretsmanager_secret" "redis_auth" {
  name        = "media-buying/${var.environment}/redis-auth"
  description = "Redis AUTH token for media-buying ${var.environment}"
  kms_key_id  = aws_kms_key.secrets.arn

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis-auth"
  })
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id = aws_secretsmanager_secret.redis_auth.id
  secret_string = var.redis_auth_token != "" ? var.redis_auth_token : ""
}

# ---- 3. Ad Platform API Keys (combined secret) ----
resource "aws_secretsmanager_secret" "api_keys" {
  name        = "media-buying/${var.environment}/ad-platform-api-keys"
  description = "API keys for all ad platform integrations - ${var.environment}"
  kms_key_id  = aws_kms_key.secrets.arn

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-ad-platform-api-keys"
  })
}

resource "aws_secretsmanager_secret_version" "api_keys" {
  secret_id = aws_secretsmanager_secret.api_keys.id
  secret_string = jsonencode({
    google_ads_api_key    = ""
    meta_ads_api_key      = ""
    tiktok_ads_api_key    = ""
    linkedin_ads_api_key  = ""
    iheart_radio_api_key  = ""
  })
}

# ---- Individual API Key Secrets (for granular access) ----
resource "aws_secretsmanager_secret" "google_ads" {
  name        = "media-buying/${var.environment}/google-ads-api-key"
  description = "Google Ads API developer token"
  kms_key_id  = aws_kms_key.secrets.arn
}

resource "aws_secretsmanager_secret_version" "google_ads" {
  secret_id = aws_secretsmanager_secret.google_ads.id
  secret_string = ""
}

resource "aws_secretsmanager_secret" "meta_ads" {
  name        = "media-buying/${var.environment}/meta-ads-api-key"
  description = "Meta Ads API access token"
  kms_key_id  = aws_kms_key.secrets.arn
}

resource "aws_secretsmanager_secret_version" "meta_ads" {
  secret_id = aws_secretsmanager_secret.meta_ads.id
  secret_string = ""
}

resource "aws_secretsmanager_secret" "tiktok_ads" {
  name        = "media-buying/${var.environment}/tiktok-ads-api-key"
  description = "TikTok Ads API key"
  kms_key_id  = aws_kms_key.secrets.arn
}

resource "aws_secretsmanager_secret_version" "tiktok_ads" {
  secret_id = aws_secretsmanager_secret.tiktok_ads.id
  secret_string = ""
}

resource "aws_secretsmanager_secret" "linkedin_ads" {
  name        = "media-buying/${var.environment}/linkedin-ads-api-key"
  description = "LinkedIn Ads API key"
  kms_key_id  = aws_kms_key.secrets.arn
}

resource "aws_secretsmanager_secret_version" "linkedin_ads" {
  secret_id = aws_secretsmanager_secret.linkedin_ads.id
  secret_string = ""
}

resource "aws_secretsmanager_secret" "iheart_radio" {
  name        = "media-buying/${var.environment}/iheart-radio-api-key"
  description = "iHeart Radio API key"
  kms_key_id  = aws_kms_key.secrets.arn
}

resource "aws_secretsmanager_secret_version" "iheart_radio" {
  secret_id = aws_secretsmanager_secret.iheart_radio.id
  secret_string = ""
}

# ==============================================================================
# PARAMETER STORE
# ==============================================================================

resource "aws_ssm_parameter" "dashboard_refresh_interval" {
  name        = "/media-buying/${var.environment}/dashboard-refresh-interval"
  description = "Dashboard auto-refresh interval in minutes"
  type        = "String"
  value       = "5"

  tags = merge(var.tags, {
    Name = "dashboard-refresh-interval"
  })
}

resource "aws_ssm_parameter" "kpi_staleness_threshold" {
  name        = "/media-buying/${var.environment}/kpi-staleness-threshold"
  description = "KPI data staleness threshold in minutes"
  type        = "String"
  value       = "15"

  tags = merge(var.tags, {
    Name = "kpi-staleness-threshold"
  })
}

resource "aws_ssm_parameter" "scoring_weights_default" {
  name        = "/media-buying/${var.environment}/scoring-weights-default"
  description = "Default scoring weights as JSON"
  type        = "String"
  value = jsonencode({
    ROAS                = 0.25
    CAC                 = 0.20
    CLTV                = 0.20
    CONVERSION_RATE     = 0.15
    SCALABILITY         = 0.10
    ATTRIBUTION_ACCURACY = 0.10
  })

  tags = merge(var.tags, {
    Name = "scoring-weights-default"
  })
}

# ==============================================================================
# DB CREDENTIALS AUTO-ROTATION LAMBDA
# ==============================================================================

resource "aws_lambda_function" "db_rotation" {
  count = var.environment == "prod" ? 1 : 0  # Only in prod initially

  filename      = "${path.module}/rotation_lambda_payload.zip"
  function_name = "media-buying-${var.environment}-db-rotation"
  role          = aws_iam_role.lambda_rotation[0].arn
  handler       = "index.handler"
  runtime       = "python3.12"
  timeout       = 60
  memory_size   = 128

  environment {
    variables = {
      SECRET_NAME = aws_secretsmanager_secret.db_credentials.name
    }
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-db-rotation"
  })
}

# IAM role for rotation Lambda
resource "aws_iam_role" "lambda_rotation" {
  count = var.environment == "prod" ? 1 : 0

  name = "media-buying-${var.environment}-db-rotation-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_rotation_basic" {
  count = var.environment == "prod" ? 1 : 0

  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  role       = aws_iam_role.lambda_rotation[0].name
}

resource "aws_iam_policy" "lambda_rotation_secrets" {
  count = var.environment == "prod" ? 1 : 0

  name   = "media-buying-${var.environment}-db-rotation-secrets"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:PutSecretValue",
          "secretsmanager:UpdateSecretVersionStage",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [aws_secretsmanager_secret.db_credentials.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "rds:DescribeDBInstances",
          "rds:ModifyDBInstance"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = "kms:Decrypt"
        Resource = aws_kms_key.secrets.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_rotation_secrets" {
  count = var.environment == "prod" ? 1 : 0

  policy_arn = aws_iam_policy.lambda_rotation_secrets[0].arn
  role       = aws_iam_role.lambda_rotation[0].name
}

resource "aws_secretsmanager_secret_rotation" "db_credentials" {
  count = var.environment == "prod" ? 1 : 0

  secret_id = aws_secretsmanager_secret.db_credentials.id
  rotate_immediately = false

  rotation_rules {
    automatically_after_days = 30
  }
}

# Note: The rotation Lambda payload ZIP needs to be created separately with:
#   mkdir -p /tmp/rotation_lambda
#   cat > /tmp/rotation_lambda/index.py << 'EOF'
#   ... (rotation Lambda code)
#   EOF
#   cd /tmp/rotation_lambda && zip -r rotation_lambda_payload.zip index.py
#   cp rotation_lambda_payload.zip infrastructure/terraform/modules/security/
