resource "aws_route53_zone" "private" {
  name = var.private_zone_name

  vpc {
    vpc_id = aws_vpc.this.id
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-private-zone"
  })
}

resource "aws_route53_record" "app" {
  for_each = aws_instance.app

  zone_id = aws_route53_zone.private.zone_id
  name    = "${each.key}.${var.private_zone_name}"
  type    = "A"
  ttl     = 60
  records = [each.value.private_ip]
}

resource "aws_route53_record" "redis" {
  count = local.is_production ? 1 : 0

  zone_id = aws_route53_zone.private.zone_id
  name    = "redis.${var.private_zone_name}"
  type    = "A"
  ttl     = 60
  records = [aws_instance.redis[0].private_ip]
}

resource "aws_route53_record" "mock_ota" {
  count = local.is_production ? 1 : 0

  zone_id = aws_route53_zone.private.zone_id
  name    = "mock-ota.${var.private_zone_name}"
  type    = "A"
  ttl     = 60
  records = [aws_instance.mock_ota[0].private_ip]
}

resource "aws_route53_record" "observability" {
  count = local.is_production ? 1 : 0

  zone_id = aws_route53_zone.private.zone_id
  name    = "observability.${var.private_zone_name}"
  type    = "A"
  ttl     = 60
  records = [aws_instance.observability[0].private_ip]
}

resource "aws_route53_record" "mongo" {
  for_each = aws_instance.mongo

  zone_id = aws_route53_zone.private.zone_id
  name    = "${each.key}.${var.private_zone_name}"
  type    = "A"
  ttl     = 60
  records = [each.value.private_ip]
}

resource "aws_route53_record" "minimal_mongo" {
  count = local.is_minimal ? 1 : 0

  zone_id = aws_route53_zone.private.zone_id
  name    = "minimal-mongo.${var.private_zone_name}"
  type    = "A"
  ttl     = 60
  records = [aws_instance.minimal_mongo[0].private_ip]
}

resource "aws_route53_record" "postgres" {
  zone_id = aws_route53_zone.private.zone_id
  name    = "postgres.${var.private_zone_name}"
  type    = "CNAME"
  ttl     = 60
  records = [aws_db_instance.postgres.address]
}
