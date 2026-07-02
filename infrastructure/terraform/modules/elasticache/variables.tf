# ------------------------------------------------------------------------------
# ElastiCache Module Variables
# ------------------------------------------------------------------------------

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "private_data_subnet_ids" {
  description = "List of private-data subnet IDs"
  type        = list(string)
}

variable "redis_security_group_id" {
  description = "Security group ID for Redis"
  type        = string
}

variable "node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}

variable "num_shards" {
  description = "Number of shards (cluster mode)"
  type        = number
  default     = 1
}

variable "replicas_per_shard" {
  description = "Number of replicas per shard"
  type        = number
  default     = 0
}

variable "multi_az_enabled" {
  description = "Enable Multi-AZ"
  type        = bool
  default     = false
}

variable "snapshot_retention_limit" {
  description = "Number of days to retain snapshots"
  type        = number
  default     = 1
}

variable "tags" {
  description = "Common tags"
  type        = map(string)
  default     = {}
}
