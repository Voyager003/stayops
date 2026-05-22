locals {
  production_app_instances = {
    app-1 = 0
    app-2 = 1
  }

  production_mongo_instances = {
    mongo-1 = 0
    mongo-2 = 1
    mongo-3 = 1
  }

  app_instances   = local.is_production ? local.production_app_instances : {}
  mongo_instances = local.is_production ? local.production_mongo_instances : {}
}

resource "aws_instance" "app" {
  for_each = local.app_instances

  ami                         = var.ami_id
  instance_type               = var.app_instance_type
  subnet_id                   = aws_subnet.private[each.value % length(aws_subnet.private)].id
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = false
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-${each.key}"
    Role = "app"
  })
}

resource "aws_instance" "redis" {
  count = local.is_production ? 1 : 0

  ami                         = var.ami_id
  instance_type               = var.redis_instance_type
  subnet_id                   = aws_subnet.private[0].id
  vpc_security_group_ids      = [aws_security_group.redis.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = false
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-redis"
    Role = "redis"
  })
}

resource "aws_instance" "mongo" {
  for_each = local.mongo_instances

  ami                         = var.ami_id
  instance_type               = var.mongo_instance_type
  subnet_id                   = aws_subnet.private[each.value % length(aws_subnet.private)].id
  vpc_security_group_ids      = [aws_security_group.mongo.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = false
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.mongo_root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-${each.key}"
    Role = "mongodb"
  })
}

resource "aws_instance" "mock_ota" {
  count = local.is_production ? 1 : 0

  ami                         = var.ami_id
  instance_type               = var.mock_ota_instance_type
  subnet_id                   = aws_subnet.private[0].id
  vpc_security_group_ids      = [aws_security_group.mock_ota.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = false
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-mock-ota"
    Role = "mock-ota"
  })
}

resource "aws_instance" "observability" {
  count = local.is_production ? 1 : 0

  ami                         = var.ami_id
  instance_type               = var.observability_instance_type
  subnet_id                   = aws_subnet.private[min(1, length(aws_subnet.private) - 1)].id
  vpc_security_group_ids      = [aws_security_group.observability.id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = false
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-observability"
    Role = "observability"
  })
}

resource "aws_instance" "minimal_app" {
  count = local.is_minimal ? 1 : 0

  ami                         = var.ami_id
  instance_type               = var.minimal_app_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.minimal_app[0].id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = true
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-minimal-app"
    Role = "minimal-app"
  })
}

resource "aws_eip" "minimal_app" {
  count = local.is_minimal ? 1 : 0

  domain = "vpc"

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-minimal-app-eip"
    Role = "minimal-app"
  })
}

resource "aws_eip_association" "minimal_app" {
  count = local.is_minimal ? 1 : 0

  instance_id   = aws_instance.minimal_app[0].id
  allocation_id = aws_eip.minimal_app[0].id
}

resource "aws_instance" "minimal_mongo" {
  count = local.is_minimal ? 1 : 0

  ami                         = var.ami_id
  instance_type               = var.minimal_mongo_instance_type
  subnet_id                   = aws_subnet.private[0].id
  vpc_security_group_ids      = [aws_security_group.minimal_mongo[0].id]
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = false
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data/docker-host.sh")

  root_block_device {
    volume_size = var.mongo_root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-minimal-mongo"
    Role = "minimal-mongodb"
  })
}
