output "alb_dns_name" {
  description = "Public ALB DNS name. Point the public API DNS record to this value."
  value       = try(aws_lb.app[0].dns_name, null)
}

output "alb_zone_id" {
  description = "ALB hosted zone ID for alias records."
  value       = try(aws_lb.app[0].zone_id, null)
}

output "app_private_ips" {
  description = "Private IPs of app EC2 instances."
  value       = { for name, instance in aws_instance.app : name => instance.private_ip }
}

output "app_instance_ids" {
  description = "Instance IDs of app EC2 instances."
  value       = { for name, instance in aws_instance.app : name => instance.id }
}

output "mongo_instance_ids" {
  description = "Instance IDs of MongoDB EC2 instances."
  value       = { for name, instance in aws_instance.mongo : name => instance.id }
}

output "redis_private_ip" {
  description = "Private IP of Redis EC2."
  value       = try(aws_instance.redis[0].private_ip, null)
}

output "mongo_private_ips" {
  description = "Private IPs of MongoDB EC2 instances."
  value       = { for name, instance in aws_instance.mongo : name => instance.private_ip }
}

output "mock_ota_private_ip" {
  description = "Private IP of Mock OTA EC2."
  value       = try(aws_instance.mock_ota[0].private_ip, null)
}

output "redis_instance_id" {
  description = "Instance ID of Redis EC2."
  value       = try(aws_instance.redis[0].id, null)
}

output "mock_ota_instance_id" {
  description = "Instance ID of Mock OTA EC2."
  value       = try(aws_instance.mock_ota[0].id, null)
}

output "observability_instance_id" {
  description = "Instance ID of Observability EC2."
  value       = try(aws_instance.observability[0].id, null)
}

output "observability_private_ip" {
  description = "Private IP of Observability EC2."
  value       = try(aws_instance.observability[0].private_ip, null)
}

output "private_zone_id" {
  description = "Route 53 private hosted zone ID."
  value       = aws_route53_zone.private.zone_id
}

output "artifact_bucket_name" {
  description = "S3 bucket for production deployment bundles."
  value       = aws_s3_bucket.artifacts.bucket
}

output "minimal_app_instance_id" {
  description = "Instance ID of the minimal public app EC2."
  value       = try(aws_instance.minimal_app[0].id, null)
}

output "minimal_app_public_ip" {
  description = "Elastic IP of the minimal public app EC2. Point the public API DNS A record to this value."
  value       = try(aws_eip.minimal_app[0].public_ip, null)
}

output "minimal_mongo_instance_id" {
  description = "Instance ID of the minimal single-node MongoDB EC2."
  value       = try(aws_instance.minimal_mongo[0].id, null)
}

output "minimal_mongo_private_ip" {
  description = "Private IP of the minimal single-node MongoDB EC2."
  value       = try(aws_instance.minimal_mongo[0].private_ip, null)
}

output "minimal_mongo_private_dns" {
  description = "Private DNS name of the minimal single-node MongoDB EC2."
  value       = local.is_minimal ? "minimal-mongo.${var.private_zone_name}" : null
}
