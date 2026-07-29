"""Adhoc data-analysis and ML tooling for WellConverge.

This package sits outside the modular monolith: it never imports from the Java
side and owns no product behaviour. It exists to (a) generate synthetic medical
data, (b) land it in S3, and (c) register it in the AWS Glue Data Catalog — the
catalog SageMaker Studio, Athena and Data Wrangler read from.
"""

__version__ = "0.1.0"
