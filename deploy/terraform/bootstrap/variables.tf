variable "aws_region" {
  description = "AWS region for the bootstrap identities (IAM is global, but the provider still needs a region)."
  type        = string
  default     = "ap-south-1"
}

variable "github_org" {
  description = "GitHub organization or user that owns the repo."
  type        = string
  default     = "luckyakhi"
}

variable "github_repo" {
  description = "GitHub repository name (without org)."
  type        = string
  default     = "wellconverge"
}

variable "github_allowed_refs" {
  description = <<-EOT
    Git refs allowed to assume the GitHub Actions deploy role, as OIDC subject patterns.
    Defaults to the main branch only, plus any environment named "production".
  EOT
  type        = list(string)
  default = [
    "repo:luckyakhi/wellconverge:ref:refs/heads/main",
  ]
}

variable "name_prefix" {
  description = "Prefix applied to every resource this stack creates, to scope down bootstrap-user permissions."
  type        = string
  default     = "wellconverge"
}

variable "state_bucket_name" {
  description = "S3 bucket holding remote Terraform state for the main stack. Must be globally unique."
  type        = string
  default     = "wellconverge-tfstate-273505519511"
}

variable "state_lock_table_name" {
  description = "DynamoDB table used for Terraform state locking."
  type        = string
  default     = "wellconverge-tfstate-lock"
}
