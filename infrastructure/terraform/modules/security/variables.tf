# ------------------------------------------------------------------------------
# Security Module Variables
# ------------------------------------------------------------------------------

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "db_password" {
  description = "Database master password (from RDS module)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_username" {
  description = "Database username"
  type        = string
  default     = "mediabuyer"
}

variable "redis_auth_token" {
  description = "Redis AUTH token"
  type        = string
  sensitive   = true
  default     = ""
}

variable "kms_key_arns" {
  description = "Map of KMS key ARNs for secret encryption"
  type        = map(string)
  default     = {}
}

variable "tags" {
  description = "Common tags"
  type        = map(string)
  default     = {}
}
