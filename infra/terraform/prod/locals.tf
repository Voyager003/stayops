locals {
  name_prefix   = "${var.project}-${var.environment}"
  is_production = var.deployment_topology == "production"
  is_minimal    = var.deployment_topology == "minimal"

  tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      Topology    = var.deployment_topology
      ManagedBy   = "terraform"
    },
    var.common_tags
  )
}
