variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "name_prefix" {
  type    = string
  default = "wellconverge"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.1.0/24", "10.20.2.0/24"]
}

variable "availability_zones" {
  description = "Must have >= 2 entries if set; empty selects the first two available AZs in aws_region."
  type        = list(string)
  default     = []
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "fargate_cpu" {
  type    = number
  default = 256
}

variable "fargate_memory" {
  type    = number
  default = 512
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "backend_image" {
  description = "Full ECR image URI:tag for the backend task. Defaults to this stack's own ECR repo, :latest tag."
  type        = string
  default     = null
}

variable "db_name" {
  type    = string
  default = "wellconverge"
}

variable "db_username" {
  type    = string
  default = "wellconverge"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_engine_version" {
  description = "Major version only, so RDS picks the latest supported minor automatically."
  type        = string
  default     = "16"
}

variable "log_retention_days" {
  type    = number
  default = 14
}
