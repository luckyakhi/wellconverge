# Remote state for this stack.
#
# Left commented until deploy/terraform/state has been applied -- naming a backend that doesn't
# exist yet makes every terraform command in this directory fail, including the ones you need to
# create it. Uncomment as step 3 of the migration (see deploy/terraform/README.md), then run:
#
#     terraform init -migrate-state
#
# Terraform will offer to copy the existing local state into the bucket. Answer yes. Afterwards
# delete the local terraform.tfstate / terraform.tfstate.backup files -- they are stale from that
# point on, and a stale local state file is a genuine hazard if it's ever picked up again.

terraform {
  backend "s3" {
    bucket         = "wellconverge-tfstate-273505519511"
    key            = "main/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "wellconverge-tfstate-lock"
    encrypt        = true
  }
}
