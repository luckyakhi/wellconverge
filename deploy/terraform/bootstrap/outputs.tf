output "github_actions_deploy_role_arn" {
  description = "Put this in the GitHub Actions workflow's `role-to-assume` input."
  value       = aws_iam_role.github_actions_deploy.arn
}

output "terraform_deployer_role_arn" {
  description = "Put this in ~/.aws/config as the `role_arn` for the deployer profile."
  value       = aws_iam_role.terraform_deployer.arn
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github_actions.arn
}
