data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = length(var.availability_zones) > 0 ? var.availability_zones : slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.name_prefix}-vpc", Project = var.name_prefix }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.name_prefix}-igw", Project = var.name_prefix }
}

# Public-only VPC by design: ECS Fargate tasks get public IPs directly (locked down via
# security group) instead of sitting behind a NAT Gateway, to avoid its ~$32-35/month fixed
# cost for a project at this traffic level. Revisit if/when this needs a private-subnet posture.
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "${var.name_prefix}-public-${count.index}", Project = var.name_prefix }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.name_prefix}-public-rt", Project = var.name_prefix }
}

resource "aws_route_table_association" "public" {
  # Counted off the variable, not off aws_subnet.public: a count derived from another resource
  # cannot be resolved when that resource isn't in state yet, which breaks `terraform import`
  # (and any plan from a clean state). Both expressions yield the same number.
  count          = length(var.public_subnet_cidrs)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}
