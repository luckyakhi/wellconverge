terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Local state deliberately: this stack creates the very first AWS identities;
  # there is no S3 bucket yet to hold remote state (see ADR-0005).
}

provider "aws" {
  region = var.aws_region
}
