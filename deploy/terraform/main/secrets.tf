# The database master password is no longer managed here.
#
# It used to be a random_password fed into both the RDS instance and a Secrets Manager secret --
# which meant the generated value was written into terraform.tfstate in plaintext, in three separate
# attributes. That blocked any shared/remote state story.
#
# RDS now owns the password end to end (see manage_master_user_password in rds.tf) and publishes it
# to a Secrets Manager secret that AWS creates and can rotate. Terraform only ever sees its ARN.
