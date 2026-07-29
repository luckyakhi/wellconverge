# The S3 data lake: one private, encrypted bucket holding the dataset under
# <data_prefix>/ and Athena query results under <athena_prefix>/.
#
# Terraform owns the bucket. `wc-tools upload` writes objects into it — data, not
# infrastructure — and fails loudly if this has not been applied. See ADR-0005.

locals {
  bucket_name = coalesce(
    var.bucket_name,
    "wellconverge-datalake-${var.aws_account_id}-${var.aws_region}",
  )
}

resource "aws_s3_bucket" "data_lake" {
  bucket        = local.bucket_name
  force_destroy = var.force_destroy

  tags = {
    Name            = local.bucket_name
    DataSensitivity = "synthetic"
    Purpose         = "Synthetic health data lake for Glue/SageMaker catalog work"
  }
}

resource "aws_s3_bucket_public_access_block" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  rule {
    object_ownership = "BucketOwnerEnforced" # ACLs off entirely
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # SSE-S3: free, and enough for synthetic data
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  versioning_configuration {
    # The dataset is deterministic — same seed regenerates it byte for byte, so
    # versioning would only accumulate cost for no recovery benefit.
    status = "Disabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "data_lake" {
  bucket = aws_s3_bucket.data_lake.id

  # Athena results are disposable; without this they accumulate forever.
  rule {
    id     = "expire-athena-results"
    status = "Enabled"

    filter {
      prefix = "${var.athena_prefix}/"
    }

    expiration {
      days = var.athena_results_expiration_days
    }
  }

  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.data_lake]
}
