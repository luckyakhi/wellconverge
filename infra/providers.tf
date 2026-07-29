provider "aws" {
  region = var.aws_region

  # The same guard the Python tooling applies before any write: refuse to touch
  # an account we did not mean to touch.
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project     = "WellConverge"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Repository  = "wellconverge/infra"
    }
  }
}
