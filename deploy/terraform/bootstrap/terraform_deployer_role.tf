# Role assumed (via STS, from a local machine) to run the main infra stack in deploy/terraform/main.
# Trusted principal is whoever applies *this* bootstrap stack — the bootstrap IAM user, by design.
# After this applies, day-to-day Terraform runs use this role's short-lived session credentials, not
# the bootstrap user's long-lived key (see ADR-0005).

data "aws_caller_identity" "bootstrap_identity" {}

data "aws_iam_policy_document" "terraform_deployer_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "AWS"
      identifiers = [data.aws_caller_identity.bootstrap_identity.arn]
    }
  }
}

resource "aws_iam_role" "terraform_deployer" {
  name                 = "${var.name_prefix}-terraform-deployer"
  assume_role_policy   = data.aws_iam_policy_document.terraform_deployer_trust.json
  max_session_duration = 3600

  tags = {
    Project = var.name_prefix
  }
}

data "aws_iam_policy_document" "terraform_deployer_permissions" {
  # Networking: VPC/subnet/route-table/NAT/IGW/security-group actions mostly don't support
  # resource-level IAM conditions in AWS — this is a documented AWS limitation, not a scoping choice.
  statement {
    sid    = "Networking"
    effect = "Allow"
    actions = [
      "ec2:Describe*",
      "ec2:CreateVpc", "ec2:DeleteVpc", "ec2:ModifyVpcAttribute",
      "ec2:CreateSubnet", "ec2:DeleteSubnet",
      "ec2:CreateRouteTable", "ec2:DeleteRouteTable", "ec2:CreateRoute", "ec2:DeleteRoute",
      "ec2:AssociateRouteTable", "ec2:DisassociateRouteTable",
      "ec2:CreateInternetGateway", "ec2:DeleteInternetGateway",
      "ec2:AttachInternetGateway", "ec2:DetachInternetGateway",
      "ec2:AllocateAddress", "ec2:ReleaseAddress",
      "ec2:CreateNatGateway", "ec2:DeleteNatGateway",
      "ec2:CreateSecurityGroup", "ec2:DeleteSecurityGroup",
      "ec2:AuthorizeSecurityGroupIngress", "ec2:AuthorizeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress", "ec2:RevokeSecurityGroupEgress",
      "ec2:CreateTags", "ec2:DeleteTags", "ec2:ModifySubnetAttribute",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "Ecr"
    effect = "Allow"
    actions = [
      "ecr:CreateRepository", "ecr:DeleteRepository", "ecr:DescribeRepositories",
      "ecr:SetRepositoryPolicy", "ecr:GetRepositoryPolicy",
      "ecr:PutLifecyclePolicy", "ecr:GetLifecyclePolicy", "ecr:DeleteLifecyclePolicy",
      "ecr:TagResource", "ecr:UntagResource", "ecr:ListTagsForResource",
    ]
    resources = ["arn:aws:ecr:*:*:repository/${var.name_prefix}-*"]
  }

  statement {
    sid    = "Ecs"
    effect = "Allow"
    actions = [
      "ecs:CreateCluster", "ecs:DeleteCluster", "ecs:DescribeClusters",
      "ecs:CreateService", "ecs:UpdateService", "ecs:DeleteService", "ecs:DescribeServices",
      "ecs:RegisterTaskDefinition", "ecs:DeregisterTaskDefinition", "ecs:DescribeTaskDefinition",
      "ecs:TagResource", "ecs:ListTagsForResource",
    ]
    resources = ["*"] # ECS largely lacks resource-level scoping for these actions.
  }

  statement {
    sid    = "Rds"
    effect = "Allow"
    actions = [
      "rds:CreateDBInstance", "rds:DeleteDBInstance", "rds:ModifyDBInstance",
      "rds:CreateDBSubnetGroup", "rds:DeleteDBSubnetGroup",
      "rds:AddTagsToResource", "rds:ListTagsForResource",
    ]
    resources = [
      "arn:aws:rds:*:*:db:${var.name_prefix}-*",
      "arn:aws:rds:*:*:subgrp:${var.name_prefix}-*",
    ]
  }

  # Describe* calls on RDS are list operations under the hood (the client filters afterwards) and
  # don't support resource-level scoping in practice — same reasoning as ec2:Describe* above.
  statement {
    sid    = "RdsDescribe"
    effect = "Allow"
    actions = [
      "rds:DescribeDBInstances", "rds:DescribeDBSubnetGroups",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "LoadBalancing"
    effect = "Allow"
    actions = [
      "elasticloadbalancing:*", # ALB/target-group actions don't support useful resource scoping pre-create.
    ]
    resources = ["*"]
  }

  statement {
    sid    = "SecretsAndLogs"
    effect = "Allow"
    actions = [
      "secretsmanager:CreateSecret", "secretsmanager:DeleteSecret", "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue", "secretsmanager:PutSecretValue", "secretsmanager:TagResource",
      "secretsmanager:UntagResource", "secretsmanager:GetResourcePolicy",
      "logs:CreateLogGroup", "logs:DeleteLogGroup", "logs:PutRetentionPolicy",
      "logs:TagResource", "logs:UntagResource", "logs:ListTagsForResource",
    ]
    resources = [
      "arn:aws:secretsmanager:*:*:secret:${var.name_prefix}/*",
      "arn:aws:logs:*:*:log-group:/ecs/${var.name_prefix}-*",
    ]
  }

  # DescribeLogGroups is a list operation and, per AWS, doesn't accept a scoped log-group
  # resource ARN — it must be requested against "*".
  statement {
    sid       = "LogsDescribe"
    effect    = "Allow"
    actions   = ["logs:DescribeLogGroups"]
    resources = ["*"]
  }

  statement {
    sid    = "TaskRoles"
    effect = "Allow"
    actions = [
      "iam:CreateRole", "iam:DeleteRole", "iam:GetRole", "iam:UpdateRole",
      "iam:PutRolePolicy", "iam:GetRolePolicy", "iam:DeleteRolePolicy", "iam:ListRolePolicies",
      "iam:AttachRolePolicy", "iam:DetachRolePolicy", "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:TagRole", "iam:UntagRole", "iam:ListRoleTags", "iam:PassRole",
    ]
    resources = ["arn:aws:iam::*:role/${var.name_prefix}-*"]
  }

  # Read access to the specific AWS-managed policy attached to the ECS task execution role.
  # Managed policies live under the "aws" account, outside the wellconverge-* resource pattern.
  statement {
    sid    = "ReadManagedPolicies"
    effect = "Allow"
    actions = [
      "iam:GetPolicy", "iam:GetPolicyVersion",
    ]
    resources = [
      "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy",
    ]
  }
}

resource "aws_iam_role_policy" "terraform_deployer_permissions" {
  name   = "${var.name_prefix}-terraform-deployer-policy"
  role   = aws_iam_role.terraform_deployer.id
  policy = data.aws_iam_policy_document.terraform_deployer_permissions.json
}
