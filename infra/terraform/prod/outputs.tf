output "alb_dns_name" {
  description = "Public ALB DNS name. Point the public API DNS record to this value."
  value       = aws_lb.app.dns_name
}

output "alb_zone_id" {
  description = "ALB hosted zone ID for alias records."
  value       = aws_lb.app.zone_id
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
  value       = aws_instance.redis.private_ip
}

output "mongo_private_ips" {
  description = "Private IPs of MongoDB EC2 instances."
  value       = { for name, instance in aws_instance.mongo : name => instance.private_ip }
}

output "mock_ota_private_ip" {
  description = "Private IP of Mock OTA EC2."
  value       = aws_instance.mock_ota.private_ip
}

output "redis_instance_id" {
  description = "Instance ID of Redis EC2."
  value       = aws_instance.redis.id
}

output "mock_ota_instance_id" {
  description = "Instance ID of Mock OTA EC2."
  value       = aws_instance.mock_ota.id
}

output "observability_instance_id" {
  description = "Instance ID of Observability EC2."
  value       = aws_instance.observability.id
}

output "observability_private_ip" {
  description = "Private IP of Observability EC2."
  value       = aws_instance.observability.private_ip
}

output "private_zone_id" {
  description = "Route 53 private hosted zone ID."
  value       = aws_route53_zone.private.zone_id
}
