# ------------------------------------------------------------------------------
# EKS Cluster Module
# Provisions EKS cluster, managed node groups, and OIDC provider for IRSA.
# ------------------------------------------------------------------------------

# ---------------
# EKS Cluster
# ---------------
resource "aws_eks_cluster" "main" {
  name     = "media-buying-${var.environment}-eks"
  role_arn = aws_iam_role.cluster.arn
  version  = var.cluster_version

  vpc_config {
    subnet_ids              = concat(var.private_app_subnet_ids, var.public_subnet_ids)
    endpoint_private_access = var.environment == "prod" ? true : false
    endpoint_public_access  = var.environment == "prod" ? false : true
    public_access_cidrs     = var.environment == "prod" ? [] : ["0.0.0.0/0"]
    security_group_ids      = [var.eks_node_security_group_id]
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  encryption_config {
    provider {
      key_arn = aws_kms_key.eks.arn
    }
    resources = ["secrets"]
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-eks"
  })

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
    aws_iam_role_policy_attachment.service_policy
  ]
}

# ---------------
# KMS Key for EKS Secrets Encryption
# ---------------
resource "aws_kms_key" "eks" {
  description             = "KMS key for EKS secrets encryption - ${var.environment}"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-eks-kms"
  })
}

resource "aws_kms_alias" "eks" {
  name          = "alias/media-buying-${var.environment}-eks"
  target_key_id = aws_kms_key.eks.key_id
}

# ---------------
# OIDC Provider for IRSA
# ---------------
data "tls_certificate" "eks" {
  url = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "main" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
}

# ---------------
# Managed Node Group: Dashboard Application
# ---------------
resource "aws_eks_node_group" "dashboard" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "media-buying-${var.environment}-dashboard-ng"
  node_role_arn   = aws_iam_role.node_group.arn
  subnet_ids      = var.private_app_subnet_ids
  version         = var.cluster_version

  instance_types = [var.dashboard_node_group_instance_type]

  scaling_config {
    desired_size = var.dashboard_node_group_desired_size
    min_size     = var.dashboard_node_group_min_size
    max_size     = var.dashboard_node_group_max_size
  }

  update_config {
    max_unavailable = 1
  }

  ami_type = "AL2023_x86_64_STANDARD"
  disk_size = 50

  labels = {
    "node-type"     = "dashboard"
    "environment"   = var.environment
    "app-component" = "dashboard"
  }

  tags = merge(var.tags, {
    Name = "media-buying-${var.environment}-dashboard-ng"
    "k8s.io/cluster-autoscaler/enabled"             = "true"
    "k8s.io/cluster-autoscaler/${aws_eks_cluster.main.name}" = "owned"
  })

  depends_on = [
    aws_iam_role_policy_attachment.node_worker_policy,
    aws_iam_role_policy_attachment.node_cni_policy,
    aws_iam_role_policy_attachment.node_ecr_policy
  ]
}

# ---------------
# EKS Add-ons
# ---------------
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "vpc-cni"
  addon_version = "v1.18.3-eksbuild.1"
}

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "coredns"
  addon_version = "v1.11.1-eksbuild.1"
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "kube-proxy"
  addon_version = "v1.31.0-eksbuild.1"
}

resource "aws_eks_addon" "ebs_csi_driver" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "aws-ebs-csi-driver"
  addon_version = "v1.36.0-eksbuild.1"

  service_account_role_arn = aws_iam_role.ebs_csi.arn
}
