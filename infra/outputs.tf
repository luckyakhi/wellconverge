# These outputs are the contract with the Python tooling. `make outputs` (or
# `terraform output -json > terraform-outputs.json`) writes them where wc-tools
# reads them, so resource names reach code as configuration rather than being
# string-built at runtime. See ADR-0005.

output "aws_account_id" {
  description = "Account the data platform lives in."
  value       = var.aws_account_id
}

output "aws_region" {
  description = "Region the data platform lives in."
  value       = var.aws_region
}

output "bucket" {
  description = "Data-lake bucket name."
  value       = aws_s3_bucket.data_lake.bucket
}

output "bucket_arn" {
  description = "Data-lake bucket ARN, for IAM policies."
  value       = aws_s3_bucket.data_lake.arn
}

output "data_prefix" {
  description = "Key prefix holding the dataset."
  value       = var.data_prefix
}

output "athena_prefix" {
  description = "Key prefix where Athena writes results."
  value       = var.athena_prefix
}

output "data_uri" {
  description = "S3 URI of the dataset root."
  value       = "s3://${aws_s3_bucket.data_lake.bucket}/${var.data_prefix}"
}

output "glue_database" {
  description = "Glue Data Catalog database name — query this from Athena/SageMaker."
  value       = aws_glue_catalog_database.health.name
}
