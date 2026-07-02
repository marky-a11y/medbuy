# ------------------------------------------------------------------------------
# Networking Module
# VPC, Subnets, NAT Gateways, VPC Endpoints, Security Groups
# ------------------------------------------------------------------------------

# ---------------
# VPC
# ---------------
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-vpc"
  })
}

# ---------------
# Public Subnets (ALB, NAT Gateways)
# ---------------
resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 1)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-public-${var.availability_zones[count.index]}"
    Tier = "public"
  })
}

# ---------------
# Private-App Subnets (EKS worker nodes)
# ---------------
resource "aws_subnet" "private_app" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 11)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-private-app-${var.availability_zones[count.index]}"
    Tier = "private-app"
  })
}

# ---------------
# Private-Data Subnets (RDS, ElastiCache)
# ---------------
resource "aws_subnet" "private_data" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 21)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-private-data-${var.availability_zones[count.index]}"
    Tier = "private-data"
  })
}

# ---------------
# Internet Gateway
# ---------------
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-igw"
  })
}

# ---------------
# Elastic IPs for NAT Gateways
# ---------------
resource "aws_eip" "nat" {
  count = var.enable_nat_gateways ? length(var.availability_zones) : 0
  domain = "vpc"

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-nat-eip-${var.availability_zones[count.index]}"
  })
}

# ---------------
# NAT Gateways (one per AZ)
# ---------------
resource "aws_nat_gateway" "main" {
  count = var.enable_nat_gateways ? length(var.availability_zones) : 0

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-nat-${var.availability_zones[count.index]}"
  })

  depends_on = [aws_internet_gateway.main]
}

# ---------------
# Public Route Table
# ---------------
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ---------------
# Private-App Route Tables (one per AZ)
# ---------------
resource "aws_route_table" "private_app" {
  count = length(var.availability_zones)

  vpc_id = aws_vpc.main.id

  dynamic "route" {
    for_each = var.enable_nat_gateways ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.main[count.index].id
    }
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-private-app-rt-${var.availability_zones[count.index]}"
  })
}

resource "aws_route_table_association" "private_app" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

# ---------------
# Private-Data Route Tables (shared with private-app routing)
# ---------------
resource "aws_route_table" "private_data" {
  count = length(var.availability_zones)

  vpc_id = aws_vpc.main.id

  dynamic "route" {
    for_each = var.enable_nat_gateways ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.main[count.index].id
    }
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-private-data-rt-${var.availability_zones[count.index]}"
  })
}

resource "aws_route_table_association" "private_data" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.private_data[count.index].id
  route_table_id = aws_route_table.private_data[count.index].id
}

# ==============================================================================
# VPC Endpoints
# ==============================================================================

resource "aws_vpc_endpoint" "s3" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id       = aws_vpc.main.id
  service_name = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"

  route_table_ids = concat(
    [aws_route_table.public.id],
    aws_route_table.private_app[*].id,
    aws_route_table.private_data[*].id
  )

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-s3-vpce"
  })
}

resource "aws_vpc_endpoint" "ecr_api" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.api"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-ecr-api-vpce"
  })
}

resource "aws_vpc_endpoint" "ecr_dkr" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.dkr"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-ecr-dkr-vpce"
  })
}

resource "aws_vpc_endpoint" "secrets_manager" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.secretsmanager"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-secretsmanager-vpce"
  })
}

resource "aws_vpc_endpoint" "cloudwatch_logs" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.logs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-logs-vpce"
  })
}

resource "aws_vpc_endpoint" "sts" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.sts"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-sts-vpce"
  })
}

data "aws_region" "current" {}

# ==============================================================================
# Security Groups
# ==============================================================================

# ---- ALB Security Group ----
resource "aws_security_group" "alb" {
  name        = "media-buying-${var.environment}-alb-sg"
  description = "Security group for ALB - allows HTTPS from internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP redirect to HTTPS"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-alb-sg"
  })
}

# ---- EKS Node Security Group ----
resource "aws_security_group" "eks_node" {
  name        = "media-buying-${var.environment}-eks-node-sg"
  description = "Security group for EKS worker nodes"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Allow traffic from ALB on app port"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Allow intra-node communication"
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    self        = true
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-eks-node-sg"
  })
}

# ---- RDS Security Group ----
resource "aws_security_group" "rds" {
  name        = "media-buying-${var.environment}-rds-sg"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Allow PostgreSQL from EKS worker nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_node.id]
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-rds-sg"
  })
}

# ---- Redis Security Group ----
resource "aws_security_group" "redis" {
  name        = "media-buying-${var.environment}-redis-sg"
  description = "Security group for ElastiCache Redis"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Allow Redis from EKS worker nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_node.id]
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-redis-sg"
  })
}

# ---- VPC Endpoints Security Group ----
resource "aws_security_group" "vpc_endpoints" {
  name        = "media-buying-${var.environment}-vpce-sg"
  description = "Security group for VPC endpoints"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTPS from private subnets"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-vpce-sg"
  })
}
