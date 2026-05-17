locals {
  app_instances = {
    app-1 = 0
    app-2 = 1
  }

  mongo_instances = {
    mongo-1 = 0
    mongo-2 = 1
    mongo-3 = 1
  }
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
