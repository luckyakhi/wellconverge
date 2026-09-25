resource "aws_db_subnet_group" "main" {
  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = aws_subnet.public[*].id

  tags = { Project = var.name_prefix }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.name_prefix}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"

  db_name  = var.db_name
  username = var.db_username

  # RDS generates the master password and writes it straight into Secrets Manager. Terraform never
  # receives the value, so it never lands in state — which is what lets this state file live in a
  # shared backend at all. The secret's ARN is exposed as master_user_secret[0].secret_arn.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  auto_minor_version_upgrade = true
  multi_az                   = false
  backup_retention_period    = 1
  skip_final_snapshot        = true
  deletion_protection        = false
  apply_immediately          = true

  tags = { Project = var.name_prefix }
}

# Stopping keeps storage and data but ends compute charges. AWS restarts a stopped instance
# automatically after 7 days, so a long pause needs pause.sh re-run weekly (ADR-0007).
resource "aws_rds_instance_state" "main" {
  identifier = aws_db_instance.main.identifier
  state      = var.paused ? "stopped" : "available"
}
