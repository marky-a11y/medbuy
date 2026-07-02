# ------------------------------------------------------------------------------
# Staging Environment Backend Configuration
# ------------------------------------------------------------------------------

terraform {
  backend "s3" {
    bucket         = "media-buying-terraform-state"
    key            = "staging/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "media-buying-terraform-locks"
  }
}
