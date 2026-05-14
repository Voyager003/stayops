resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Public ALB security group"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-alb-sg"
  })
}

resource "aws_security_group" "app" {
  name        = "${local.name_prefix}-app-sg"
  description = "StayOps app security group"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-app-sg"
  })
}

resource "aws_security_group" "mongo" {
  name        = "${local.name_prefix}-mongo-sg"
  description = "MongoDB replica set security group"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-mongo-sg"
  })
}

resource "aws_security_group" "redis" {
  name        = "${local.name_prefix}-redis-sg"
  description = "Redis security group"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-redis-sg"
  })
}

resource "aws_security_group" "mock_ota" {
  name        = "${local.name_prefix}-mock-ota-sg"
  description = "Mock OTA security group"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-mock-ota-sg"
  })
}

resource "aws_security_group" "observability" {
  name        = "${local.name_prefix}-observability-sg"
  description = "Observability security group"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-observability-sg"
  })
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  for_each = toset(var.allowed_http_cidrs)

  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = each.value
  from_port         = 80
  ip_protocol       = "tcp"
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  for_each = toset(var.allowed_https_cidrs)

  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = each.value
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_vpc_security_group_ingress_rule" "app_node_from_observability" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 9100
  ip_protocol                  = "tcp"
  to_port                      = 9100
}

resource "aws_vpc_security_group_egress_rule" "app_to_mongo" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.mongo.id
  from_port                    = 27017
  ip_protocol                  = "tcp"
  to_port                      = 27017
}

resource "aws_vpc_security_group_egress_rule" "app_to_redis" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 6379
  ip_protocol                  = "tcp"
  to_port                      = 6379
}

resource "aws_vpc_security_group_egress_rule" "app_to_mock_ota" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.mock_ota.id
  from_port                    = 80
  ip_protocol                  = "tcp"
  to_port                      = 80
}

resource "aws_vpc_security_group_egress_rule" "app_to_loki" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_egress_rule" "app_to_https" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "mongo_from_app" {
  security_group_id            = aws_security_group.mongo.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 27017
  ip_protocol                  = "tcp"
  to_port                      = 27017
}

resource "aws_vpc_security_group_ingress_rule" "mongo_from_self" {
  security_group_id            = aws_security_group.mongo.id
  referenced_security_group_id = aws_security_group.mongo.id
  from_port                    = 27017
  ip_protocol                  = "tcp"
  to_port                      = 27017
}

resource "aws_vpc_security_group_ingress_rule" "mongo_exporter_from_observability" {
  security_group_id            = aws_security_group.mongo.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 9216
  ip_protocol                  = "tcp"
  to_port                      = 9216
}

resource "aws_vpc_security_group_ingress_rule" "mongo_node_from_observability" {
  security_group_id            = aws_security_group.mongo.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 9100
  ip_protocol                  = "tcp"
  to_port                      = 9100
}

resource "aws_vpc_security_group_egress_rule" "mongo_to_self" {
  security_group_id            = aws_security_group.mongo.id
  referenced_security_group_id = aws_security_group.mongo.id
  from_port                    = 27017
  ip_protocol                  = "tcp"
  to_port                      = 27017
}

resource "aws_vpc_security_group_egress_rule" "mongo_to_loki" {
  security_group_id            = aws_security_group.mongo.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_egress_rule" "mongo_to_https" {
  security_group_id = aws_security_group.mongo.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 6379
  ip_protocol                  = "tcp"
  to_port                      = 6379
}

resource "aws_vpc_security_group_ingress_rule" "redis_exporter_from_observability" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 9121
  ip_protocol                  = "tcp"
  to_port                      = 9121
}

resource "aws_vpc_security_group_ingress_rule" "redis_node_from_observability" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 9100
  ip_protocol                  = "tcp"
  to_port                      = 9100
}

resource "aws_vpc_security_group_egress_rule" "redis_to_loki" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_egress_rule" "redis_to_https" {
  security_group_id = aws_security_group.redis.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "mock_ota_from_app" {
  security_group_id            = aws_security_group.mock_ota.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 80
  ip_protocol                  = "tcp"
  to_port                      = 80
}

resource "aws_vpc_security_group_ingress_rule" "mock_ota_node_from_observability" {
  security_group_id            = aws_security_group.mock_ota.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 9100
  ip_protocol                  = "tcp"
  to_port                      = 9100
}

resource "aws_vpc_security_group_egress_rule" "mock_ota_to_loki" {
  security_group_id            = aws_security_group.mock_ota.id
  referenced_security_group_id = aws_security_group.observability.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_egress_rule" "mock_ota_to_https" {
  security_group_id = aws_security_group.mock_ota.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "observability_loki_from_app" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_ingress_rule" "observability_loki_from_mongo" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.mongo.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_ingress_rule" "observability_loki_from_redis" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_ingress_rule" "observability_loki_from_mock_ota" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.mock_ota.id
  from_port                    = 3100
  ip_protocol                  = "tcp"
  to_port                      = 3100
}

resource "aws_vpc_security_group_egress_rule" "observability_to_app_metrics" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_vpc_security_group_egress_rule" "observability_to_node_exporters" {
  for_each = {
    app      = aws_security_group.app.id
    mongo    = aws_security_group.mongo.id
    redis    = aws_security_group.redis.id
    mock_ota = aws_security_group.mock_ota.id
  }

  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = each.value
  from_port                    = 9100
  ip_protocol                  = "tcp"
  to_port                      = 9100
}

resource "aws_vpc_security_group_egress_rule" "observability_to_mongo_exporter" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.mongo.id
  from_port                    = 9216
  ip_protocol                  = "tcp"
  to_port                      = 9216
}

resource "aws_vpc_security_group_egress_rule" "observability_to_redis_exporter" {
  security_group_id            = aws_security_group.observability.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 9121
  ip_protocol                  = "tcp"
  to_port                      = 9121
}

resource "aws_vpc_security_group_egress_rule" "observability_to_https" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}
