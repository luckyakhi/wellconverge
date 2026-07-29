variable "aws_region" {
  description = "Region for every resource in this module."
  type        = string
  default     = "ap-south-1"
}

variable "aws_account_id" {
  description = "Account this module is allowed to apply to. Terraform refuses any other."
  type        = string
  default     = "273505519511"

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account id."
  }
}

variable "environment" {
  description = "Environment tag applied to every resource."
  type        = string
  default     = "dev"
}

variable "bucket_name" {
  description = <<-EOT
    Data-lake bucket name. Leave null to use the convention
    wellconverge-datalake-<account>-<region>. Bucket names are globally unique,
    so account + region keeps it collision free.
  EOT
  type        = string
  default     = null
}

variable "data_prefix" {
  description = "Key prefix holding the dataset. Tables live at <prefix>/<table>/."
  type        = string
  default     = "raw"
}

variable "athena_prefix" {
  description = "Key prefix where Athena writes query results."
  type        = string
  default     = "athena-results"
}

variable "glue_database_name" {
  description = "Glue Data Catalog database. This is what SageMaker and Athena resolve tables from."
  type        = string
  default     = "wellconverge_health"

  validation {
    condition     = can(regex("^[a-z0-9_]+$", var.glue_database_name))
    error_message = "Glue database names must be lowercase alphanumeric with underscores."
  }
}

variable "athena_results_expiration_days" {
  description = "Days before Athena result files are expired. They are disposable by nature."
  type        = number
  default     = 14
}

variable "force_destroy" {
  description = <<-EOT
    Allow `terraform destroy` to delete a non-empty bucket. True is deliberate here:
    this holds regenerable synthetic data in a learning account, and a teardown that
    silently fails is worse than one that works. Set false for anything real.
  EOT
  type        = bool
  default     = true
}
