terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # State is local for now. Move to an S3 backend + DynamoDB locking as soon as a
  # second machine or person applies — see ADR-0005 and infra/README.md.
  #
  # backend "s3" {
  #   bucket         = "wellconverge-tfstate-273505519511"
  #   key            = "data-platform/terraform.tfstate"
  #   region         = "ap-south-1"
  #   dynamodb_table = "wellconverge-tfstate-locks"
  #   encrypt        = true
  # }
}
