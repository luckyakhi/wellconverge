# DB master credentials, generated once and stored in Secrets Manager. The ECS task execution
# role reads these at container start (see ecs.tf); nothing else needs them.

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.name_prefix}/db-credentials"
  description = "RDS master credentials for ${var.name_prefix}"

  tags = { Project = var.name_prefix }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.db.result
  })
}
