output "alb_dns_name" {
  description = "Public URL for the backend: http://<this>/actuator/health. Null while paused."
  value       = one(aws_lb.main[*].dns_name)
}

output "ecr_backend_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.backend.name
}

output "db_endpoint" {
  value = aws_db_instance.main.endpoint
}

output "db_secret_arn" {
  description = "RDS-managed master credentials secret. AWS owns its contents; Terraform only sees the ARN."
  # one() rather than [0]: the list is empty until RDS has actually been switched to a managed
  # password, and an output is evaluated on every plan -- including the targeted phase-1 apply that
  # performs that switch. [0] would hard-error there.
  value = one(aws_db_instance.main.master_user_secret[*].secret_arn)
}

output "paused" {
  value = var.paused
}
