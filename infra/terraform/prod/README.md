# StayOps Production Terraform

이 디렉터리는 StayOps의 production-like AWS 인프라를 Terraform으로 구성한다.

현재 목표는 기존에 수동으로 운영하던 public App EC2와 MongoDB EC2를 버리고,
Terraform이 새 인프라를 처음부터 생성하도록 만드는 것이다.

## Terraform이 생성하는 것

`terraform apply`를 실행하면 다음 AWS 리소스를 생성한다.

- VPC, public/private subnet, route table, Internet Gateway, NAT Gateway
- ALB, App, MongoDB, Redis, Mock OTA, Observability용 Security Group
- Session Manager 접속을 위한 EC2 IAM Role, Instance Profile
- App EC2 2대
- MongoDB EC2 3대
- Redis EC2 1대
- Mock OTA EC2 1대
- Observability EC2 1대
- Internet-facing ALB, Target Group, HTTP/HTTPS Listener
- `/actuator/prometheus` 외부 차단용 ALB Listener Rule
- Route 53 Private Hosted Zone
- 내부 DNS Record

최종 Terraform-managed EC2는 8대다.

```text
App EC2 x 2
MongoDB EC2 x 3
Redis EC2 x 1
Mock OTA EC2 x 1
Observability EC2 x 1
```

기존 수동 public App EC2와 기존 수동 MongoDB EC2는 Terraform state에 없으므로
Terraform이 자동으로 삭제하지 않는다.

따라서 새 인프라 검증 후 기존 EC2는 AWS 콘솔/CLI에서 직접 종료하거나,
별도 작업으로 `terraform import` 후 명시적으로 제거해야 한다.

## Terraform이 하지 않는 것

Terraform은 AWS 인프라를 만드는 도구다. 현재 구성에서는 다음 작업을 자동으로 끝내지 않는다.

- 각 EC2에 `.env` 파일 배치
- Docker Compose stack 실행
- MongoDB keyfile 생성
- MongoDB replica set bootstrap
- MongoDB application/exporter 계정 생성
- public DNS를 ALB로 전환
- 기존 수동 EC2 제거

즉 `terraform apply` 후에도 서버별 초기 배포 작업이 필요하다.

```text
terraform apply
  -> AWS 리소스 생성
  -> Session Manager로 EC2 접속
  -> 역할별 Docker Compose 실행
  -> MongoDB replica set 초기화
  -> ALB target health 확인
  -> DNS 전환
```

## Docker Compose는 Terraform에서 실행할 수 있나

가능은 하다. Terraform의 `aws_instance.user_data`로 EC2 첫 부팅 시 shell script나
cloud-init을 실행할 수 있다. 현재도 `user-data/docker-host.sh`를 사용해 Docker와 Git을 설치한다.

하지만 모든 Docker Compose 배포를 Terraform에 넣는 것은 신중해야 한다.

Terraform은 인프라의 목표 상태를 관리하는 도구이고, Docker image 교체나 compose 재배포는
애플리케이션 배포에 가깝다. 앱 배포까지 Terraform `apply`에 묶으면 앱 코드 변경 때마다
인프라 apply가 필요해지고, 인프라 변경과 앱 배포의 실패 범위가 섞인다.

현재 권장 구분은 다음과 같다.

```text
Terraform
  -> VPC, subnet, SG, ALB, EC2, IAM, Route 53 생성

배포 스크립트 또는 GitHub Actions
  -> EC2에 .env 배치
  -> docker compose pull
  -> docker compose up -d
  -> MongoDB bootstrap
```

초기 학습 단계에서는 Terraform으로 EC2를 만들고, compose 실행은 Session Manager로 접속해
수동으로 검증하는 방식이 안전하다. 이후 안정화되면 SSM Run Command, State Manager,
GitHub Actions 배포 job, 또는 systemd unit으로 자동화한다.

## 인스턴스 재시작 시 Docker를 자동으로 올리는 방법

EC2를 stop/start 하거나 reboot하면 Docker daemon도 다시 시작된다.
컨테이너를 자동으로 올리려면 두 가지가 필요하다.

1. Docker 서비스가 부팅 시 자동 시작되어야 한다.
2. 컨테이너 또는 compose service에 restart policy가 있어야 한다.

현재 `user-data/docker-host.sh`는 Docker를 설치하고 다음을 실행한다.

```bash
systemctl enable docker
systemctl start docker
```

그리고 production Docker Compose 파일들은 대부분 다음 정책을 사용한다.

```yaml
restart: unless-stopped
```

Docker 공식 문서 기준 restart policy는 컨테이너가 종료되거나 Docker daemon이 재시작될 때
컨테이너를 자동 재시작할지 제어한다. Compose 파일의 `restart: unless-stopped`는 사용자가
명시적으로 중지하지 않은 컨테이너를 다시 올리는 정책이다.

따라서 한 번 `docker compose up -d`로 컨테이너가 생성된 뒤라면,
EC2 재시작 시 Docker daemon이 올라오고 `restart: unless-stopped` 정책에 따라 컨테이너도
다시 올라온다.

단, 새 EC2가 처음 생성되는 순간부터 compose stack까지 자동 실행하려면 별도 bootstrap이 필요하다.

대표적인 방법은 다음과 같다.

- EC2 user data 또는 cloud-init에서 compose 파일 배치 후 `docker compose up -d` 실행
- systemd unit을 만들어 `docker compose up -d`를 서비스로 관리
- AWS Systems Manager Run Command로 초기 배포 명령 실행
- AWS Systems Manager State Manager로 원하는 명령을 반복 적용
- GitHub Actions에서 Session Manager 또는 SSH를 통해 compose 배포

운영 관점에서는 `user_data`에 secret을 직접 넣지 않는 것이 중요하다.
DB password, Toss secret, MongoDB keyfile 같은 값은 Git이나 Terraform state에 남기지 말고,
SSM Parameter Store, Secrets Manager, 또는 GitHub Environment Secret에서 주입하는 방식을 사용한다.

## AWS 관련 정보는 어디에 들어가는가

Terraform 코드에는 AWS 계정 정보를 직접 적지 않는다.

AWS 계정 접근 권한은 실행 주체가 제공한다.

```text
로컬 실행
  -> AWS CLI profile, SSO, 환경변수

GitHub Actions 실행
  -> OIDC로 AWS IAM Role assume
```

GitHub Actions에서는 다음 Repository Secret 또는 Environment Secret이 필요하다.

```text
AWS_TERRAFORM_ROLE_ARN
TF_STATE_BUCKET
TF_STATE_KEY
TFVARS_PROD
```

각 값의 의미는 다르다.

- `AWS_TERRAFORM_ROLE_ARN`: GitHub Actions가 assume할 AWS IAM Role ARN
- `TF_STATE_BUCKET`: Terraform state를 저장할 S3 bucket
- `TF_STATE_KEY`: S3 bucket 안에서 state 파일이 저장될 key
- `TFVARS_PROD`: `prod.tfvars`와 같은 내용의 Terraform 입력값

`TFVARS_PROD`에는 다음 같은 인프라 입력값이 들어간다.

```hcl
aws_region = "ap-northeast-2"
ami_id = "ami-..."
acm_certificate_arn = "arn:aws:acm:..."
app_instance_type = "t3.small"
mongo_instance_type = "t3.small"
redis_instance_type = "t3.micro"
```

장기 AWS access key를 GitHub Secret에 넣는 방식은 피한다.
GitHub Actions OIDC와 AWS IAM Role을 사용하는 것이 기본 방향이다.

## 로컬 검증

```bash
cp terraform.tfvars.example prod.tfvars

terraform init \
  -backend-config="bucket=<state-bucket>" \
  -backend-config="key=prod/terraform.tfstate" \
  -backend-config="region=ap-northeast-2" \
  -backend-config="encrypt=true"

terraform fmt -recursive
terraform validate
terraform plan -var-file=prod.tfvars
```

`prod.tfvars`는 Git에 올리지 않는다.

## GitHub Actions 동작

Terraform 전용 workflow는 `.github/workflows/terraform.yml`이다.

PR에서 실행되는 작업:

```text
terraform fmt -check -recursive
terraform validate
terraform plan
```

실제 반영:

```text
workflow_dispatch
  -> terraform plan -out=tfplan
  -> production-infra environment 승인
  -> terraform apply tfplan
```

앱 배포 workflow와 Terraform workflow는 분리한다.

```text
앱 배포
  -> Docker image build/push
  -> App 배포

인프라 배포
  -> Terraform plan/apply
  -> AWS 리소스 생성/변경
```

앱 코드만 변경될 때는 Terraform apply를 실행하지 않는다.
인프라 코드가 변경될 때만 Terraform workflow를 실행한다.

## Apply 이후 실행 순서

Terraform apply 후 다음 순서로 진행한다.

1. `terraform output`으로 새 EC2 instance id와 private IP를 확인한다.
2. Session Manager로 Observability EC2에 접속해 compose stack을 올린다.
3. Redis EC2에 접속해 Redis compose stack을 올린다.
4. Mock OTA EC2에 접속해 `.htpasswd`와 `.env`를 만든 뒤 compose stack을 올린다.
5. MongoDB EC2 3대에 keyfile과 `.env`를 배치하고 MongoDB compose stack을 올린다.
6. MongoDB node 하나에서 `bootstrap-replica-set.sh`를 실행한다.
7. MongoDB node 하나에서 `provision-mongo-users.sh`를 실행한다.
8. App EC2 2대에 `.env`를 배치하고 App compose stack을 올린다.
9. ALB target group에서 App EC2 2대가 healthy인지 확인한다.
10. `api.example.com` 같은 public DNS를 새 ALB로 전환한다.
11. Prometheus target과 Loki log 수집을 확인한다.
12. 기존 수동 public App EC2와 기존 수동 MongoDB EC2를 종료한다.

## 참고 문서

- Terraform AWS `aws_instance` user_data: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/instance
- AWS EC2 user data: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/user-data.html
- Docker restart policies: https://docs.docker.com/engine/containers/start-containers-automatically/
- Docker Compose service restart: https://docs.docker.com/reference/compose-file/services/#restart
- AWS Systems Manager Run Command: https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html
- AWS Systems Manager State Manager: https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-state.html
