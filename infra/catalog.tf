# The Glue Data Catalog database — the namespace SageMaker Studio, Data Wrangler,
# Athena and EMR resolve tables from.
#
# Terraform owns the *database*. The tables inside it are registered by
# `wc-tools catalog`, deliberately: their columns are derived from
# tools/src/wellconverge_tools/datagen/schema.py, which is the single source of
# truth shared with the code that writes the Parquet files. Restating ~40 columns
# in HCL would create exactly the schema drift that design exists to prevent, and
# partitions are data — discovered from the files, not declared by hand.

resource "aws_glue_catalog_database" "health" {
  name         = var.glue_database_name
  description  = "WellConverge synthetic health data lake. Fabricated records only — no real patient information."
  location_uri = "s3://${aws_s3_bucket.data_lake.bucket}/${var.data_prefix}"

  tags = {
    Name            = var.glue_database_name
    DataSensitivity = "synthetic"
  }
}
