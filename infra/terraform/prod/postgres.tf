resource "aws_db_subnet_group" "postgres" {
  name       = "${local.name_prefix}-postgres-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-postgres-subnet-group"
    Role = "postgres"
  })
}

resource "aws_db_instance" "postgres" {
  identifier = "${local.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = var.postgres_engine_version
  instance_class = var.postgres_instance_class

  allocated_storage     = var.postgres_allocated_storage_gb
  max_allocated_storage = var.postgres_max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.postgres_database_name
  username = var.postgres_master_username
  port     = 5432

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  publicly_accessible    = false

  multi_az                = var.postgres_multi_az
  backup_retention_period = var.postgres_backup_retention_days
  backup_window           = var.postgres_backup_window
  maintenance_window      = var.postgres_maintenance_window

  auto_minor_version_upgrade   = true
  copy_tags_to_snapshot        = true
  deletion_protection          = var.postgres_deletion_protection
  skip_final_snapshot          = var.postgres_skip_final_snapshot
  final_snapshot_identifier    = var.postgres_skip_final_snapshot ? null : "${local.name_prefix}-postgres-final-snapshot"
  performance_insights_enabled = var.postgres_performance_insights_enabled

  enabled_cloudwatch_logs_exports = [
    "postgresql",
    "upgrade"
  ]

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-postgres"
    Role = "postgres"
  })
}
