# Remote-state backend for the `main` stack: an encrypted, versioned S3 bucket plus a DynamoDB
# lock table.
#
# Why this is its own stack and not part of `main/`: if `main/` managed the bucket holding its own
# state, `terraform destroy` on `main/` would try to delete the bucket out from under itself. And it
# isn't in `bootstrap/` because that stack is applied by the deliberately-tiny bootstrap IAM user,
# which has no S3 or DynamoDB permissions — only the deployer role does.
#
# This stack's own state stays local and gitignored. That's acceptable where `main/`'s was not: it
# holds two trivially re-importable resources and no secrets, whereas `main/` tracks 28 resources
# and (until this change) a live database password.

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "state_bucket_name" {
  description = "Must match bootstrap's state_bucket_name, which scopes the deployer role's S3 access."
  type        = string
  default     = "wellconverge-tfstate-273505519511"
}

variable "state_lock_table_name" {
  type    = string
  default = "wellconverge-tfstate-lock"
}

variable "name_prefix" {
  type    = string
  default = "wellconverge"
}

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  # Losing this bucket means losing track of every resource in the main stack.
  lifecycle {
    prevent_destroy = true
  }

  tags = { Project = var.name_prefix }
}

# Versioning is the actual recovery mechanism: a corrupted or truncated state push can be rolled
# back to the previous object version.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Terraform takes a lock item here for the duration of a plan/apply, so two machines can't write
# state concurrently — the thing committing state to git could never give us.
resource "aws_dynamodb_table" "state_lock" {
  name         = var.state_lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = { Project = var.name_prefix }
}

output "state_bucket" {
  value = aws_s3_bucket.state.id
}

output "state_lock_table" {
  value = aws_dynamodb_table.state_lock.name
}
