# GitHub's OIDC token issuer. We fetch its current TLS chain thumbprint at apply time rather than
# hardcoding one, since GitHub has rotated the signing CA before and a stale thumbprint silently
# breaks federation.
data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]

  tags = {
    Project = var.name_prefix
  }
}
