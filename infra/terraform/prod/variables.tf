variable "project" {
  description = "Project name used for resource names and tags."
  type        = string
  default     = "stayops"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  description = "CIDR block for the production VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones used by public/private subnets."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "At least two availability zones are required for the public ALB."
  }
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDR blocks. At least two are required for ALB."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_cidrs) >= 2
    error_message = "At least two public subnets are required for the public ALB."
  }
}

variable "private_subnet_cidrs" {
  description = "Private subnet CIDR blocks used by EC2 services."
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_cidrs) >= 2
    error_message = "At least two private subnets are required for the planned topology."
  }
}

variable "ami_id" {
  description = "AMI ID for EC2 instances."
  type        = string
}

variable "key_name" {
  description = "Optional EC2 key pair name. Leave null when using Session Manager only."
  type        = string
  default     = null
}

variable "app_instance_type" {
  description = "Instance type for StayOps app EC2 instances."
  type        = string
  default     = "t3.small"
}

variable "redis_instance_type" {
  description = "Instance type for Redis EC2."
  type        = string
  default     = "t3.micro"
}

variable "mock_ota_instance_type" {
  description = "Instance type for Mock OTA EC2."
  type        = string
  default     = "t3.micro"
}

variable "observability_instance_type" {
  description = "Instance type for Observability EC2."
  type        = string
  default     = "t3.small"
}

variable "mongo_instance_type" {
  description = "Instance type for MongoDB replica set EC2 instances."
  type        = string
  default     = "t3.small"
}

variable "root_volume_size_gb" {
  description = "Root EBS volume size in GiB."
  type        = number
  default     = 30
}

variable "mongo_root_volume_size_gb" {
  description = "Root EBS volume size in GiB for MongoDB EC2 instances."
  type        = number
  default     = 50
}

variable "acm_certificate_arn" {
  description = "ACM certificate ARN for the public API HTTPS listener."
  type        = string
}

variable "private_zone_name" {
  description = "Route 53 private hosted zone name."
  type        = string
  default     = "stayops.internal"
}

variable "allowed_http_cidrs" {
  description = "CIDR ranges allowed to reach the public ALB HTTP listener."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "allowed_https_cidrs" {
  description = "CIDR ranges allowed to reach the public ALB HTTPS listener."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "common_tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
}

variable "artifact_bucket_name" {
  description = "Optional S3 bucket name for production deployment bundles. Defaults to a deterministic account-scoped name."
  type        = string
  default     = null
}

variable "artifact_bucket_force_destroy" {
  description = "Whether Terraform may delete the deployment artifact bucket even when objects remain."
  type        = bool
  default     = false
}

variable "github_actions_role_name" {
  description = "Optional IAM role name assumed by GitHub Actions. When set, Terraform attaches S3/SSM deploy permissions to it."
  type        = string
  default     = null
}
