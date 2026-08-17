terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Local state, same rationale as deploy/terraform/bootstrap: no S3 backend + DynamoDB lock
  # table exist yet. Migrating to a remote backend is tracked as a follow-up (see ADR-0005).
}

provider "aws" {
  region = var.aws_region
}
