# StayOps Production Terraform

이 디렉터리는 StayOps의 production-like AWS 인프라와 비용 절감용 minimal 인프라를
Terraform으로 구성한다.

운영 중 실수 삭제를 막기 위해 production과 minimal은 같은 Terraform root를 사용하되
서로 다른 S3 backend key와 tfvars로 state를 분리한다.

## Terraform이 생성하는 것

`deployment_topology = "production"`으로 `terraform apply`를 실행하면 다음 AWS 리소스를 생성한다.

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
- 초기 배포 bundle 저장용 S3 artifact bucket

최종 Terraform-managed EC2는 8대다.

```text
App EC2 x 2
MongoDB EC2 x 3
Redis EC2 x 1
Mock OTA EC2 x 1
Observability EC2 x 1
```

`deployment_topology = "minimal"`과 `environment = "minimal"`로 실행하면 다음 저비용 리소스를 생성한다.

```text
Public App EC2 x 1
Private single-node MongoDB EC2 x 1
Public subnet x 1
Private subnet x 1
Elastic IP x 1
Route 53 Private Hosted Zone / minimal-mongo private DNS
S3 artifact bucket
Session Manager용 IAM Role / Instance Profile
```

minimal App EC2는 Nginx, StayOps app, Redis, Mock OTA, Mock OTA MongoDB를 같은 Docker Compose
stack으로 실행한다. minimal MongoDB는 transaction 지원을 위해 standalone이 아니라 single-node
replica set으로 실행한다.
minimal은 최소 작동 구성이므로 기본 예시는 단일 AZ에 public subnet 1개, private subnet 1개만 둔다.

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

초기 검증 이후에는 `.github/workflows/bootstrap-deploy.yml`을 사용해 SSM Run Command로
역할별 Docker Compose stack을 배포한다. 이 방식은 private subnet EC2에 SSH로 접속하지 않고,
전체 GitHub repository를 각 인스턴스에 clone하지 않는다.

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

현재 자동화 방향은 다음과 같다.

```text
GitHub Actions
  -> 역할별 compose/config bundle 생성
  -> S3 artifact bucket 업로드
  -> SSM Run Command 실행

EC2
  -> S3에서 자기 역할 bundle 다운로드
  -> Parameter Store에서 /stayops/prod/<role>/ 값 조회
  -> /opt/stayops/<role>/.env 생성
  -> docker compose pull && docker compose up -d
  -> systemd unit 등록으로 EC2 재시작 후 compose up 보장
```

## production과 minimal 전환 방식

production과 minimal을 같은 state에서 `deployment_topology`만 바꿔 전환하지 않는다.
그 방식은 Terraform이 기존 production 리소스를 삭제하고 minimal 리소스를 만들도록 계획할 수 있어
운영 리스크가 크다.

대신 state key를 분리한다.

```text
production -> TF_STATE_KEY_PRODUCTION, TFVARS_PRODUCTION
minimal    -> TF_STATE_KEY_MINIMAL, TFVARS_MINIMAL
```

GitHub Actions의 Terraform workflow는 PR에서 두 topology를 모두 plan하고, 수동 apply에서는 입력한
topology의 state key와 tfvars만 사용한다.

## Terraform으로 리소스 삭제하기

콘솔에서 EC2, ALB, NAT Gateway, Security Group을 하나씩 삭제하지 않는다.
Terraform이 만든 리소스는 `.github/workflows/terraform-destroy.yml`에서 topology와 확인 문구를
입력한 뒤 `terraform plan -destroy`와 `terraform apply`로 삭제한다.

```text
topology=minimal
confirm=destroy-minimal
```

production 삭제는 같은 방식이지만 `confirm=destroy-production`을 요구한다. 이 작업은
GitHub Environment 승인을 거쳐야 한다.

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
TF_STATE_KEY_PRODUCTION
TF_STATE_KEY_MINIMAL
TFVARS_PRODUCTION
TFVARS_MINIMAL
```

각 값의 의미는 다르다.

- `AWS_TERRAFORM_ROLE_ARN`: GitHub Actions가 assume할 AWS IAM Role ARN
- `TF_STATE_BUCKET`: Terraform state를 저장할 S3 bucket
- `TF_STATE_KEY_PRODUCTION`: production state 파일이 저장될 S3 key
- `TF_STATE_KEY_MINIMAL`: minimal state 파일이 저장될 S3 key
- `TFVARS_PRODUCTION`: production용 tfvars
- `TFVARS_MINIMAL`: minimal용 tfvars

기존 호환을 위해 `TF_STATE_KEY`, `TFVARS_PROD`가 있으면 production fallback으로 사용할 수 있다.

Bootstrap deploy workflow는 같은 `AWS_TERRAFORM_ROLE_ARN`을 사용한다. Artifact bucket 이름을
기본값이 아닌 값으로 만들었다면 GitHub Actions variable도 추가한다.

```text
BOOTSTRAP_ARTIFACT_BUCKET_PRODUCTION=<production artifact bucket name>
BOOTSTRAP_ARTIFACT_BUCKET_MINIMAL=<minimal artifact bucket name>
```

기본값을 사용하면 workflow가 AWS account id로 다음 이름을 계산한다.

```text
stayops-prod-artifacts-<account-id>
stayops-minimal-artifacts-<account-id>
```

`TFVARS_PRODUCTION`, `TFVARS_MINIMAL`에는 다음 같은 인프라 입력값이 들어간다.

```hcl
aws_region = "ap-northeast-2"
deployment_topology = "production"
environment = "prod"
ami_id = "ami-..."
acm_certificate_arn = "arn:aws:acm:..."
app_instance_type = "t3.small"
mongo_instance_type = "t3.small"
redis_instance_type = "t3.micro"
github_actions_role_name = "stayops-github-terraform-role"
```

minimal용 `TFVARS_MINIMAL`은 단일 AZ 기준으로 둔다.

```hcl
aws_region = "ap-northeast-2"

deployment_topology = "minimal"
environment         = "minimal"

availability_zones = ["ap-northeast-2a"]

vpc_cidr = "10.10.0.0/16"

public_subnet_cidrs  = ["10.10.101.0/24"]
private_subnet_cidrs = ["10.10.10.0/24"]

ami_id = "ami-..."

minimal_app_instance_type   = "t3.small"
minimal_mongo_instance_type = "t3.small"

root_volume_size_gb       = 30
mongo_root_volume_size_gb = 50

github_actions_role_name = "stayops-github-terraform-role"
```

장기 AWS access key를 GitHub Secret에 넣는 방식은 피한다.
GitHub Actions OIDC와 AWS IAM Role을 사용하는 것이 기본 방향이다.

## 로컬 검증

```bash
cp terraform.tfvars.example prod.tfvars
# 또는 minimal 검증 시:
# cp terraform.minimal.tfvars.example minimal.tfvars

terraform init \
  -backend-config="bucket=<state-bucket>" \
  -backend-config="key=production/terraform.tfstate" \
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

## Parameter Store 값

초기 배포 자동화는 AWS Systems Manager Parameter Store를 사용한다.

권장 prefix:

```text
/stayops/prod/app/
/stayops/prod/mongodb/
/stayops/prod/redis/
/stayops/prod/mock-ota/
/stayops/prod/observability/
/stayops/minimal/app/
/stayops/minimal/mongodb/
```

민감값은 `SecureString`으로 저장한다.

최소 예시:

```text
/stayops/prod/app/SPRING_MONGODB_URI
/stayops/prod/app/SPRING_DATA_REDIS_HOST
/stayops/prod/app/SPRING_DATA_REDIS_PORT
/stayops/prod/app/TOSS_SECRET_KEY
/stayops/prod/app/MOCK_OTA_ENDPOINT
/stayops/prod/app/GHCR_USERNAME
/stayops/prod/app/GHCR_TOKEN

/stayops/prod/mongodb/MONGO_REPLICA_SET
/stayops/prod/mongodb/MONGO1_HOST
/stayops/prod/mongodb/MONGO2_HOST
/stayops/prod/mongodb/MONGO3_HOST
/stayops/prod/mongodb/MONGO_INITDB_ROOT_USERNAME
/stayops/prod/mongodb/MONGO_INITDB_ROOT_PASSWORD
/stayops/prod/mongodb/MONGO_APP_USERNAME
/stayops/prod/mongodb/MONGO_APP_PASSWORD
/stayops/prod/mongodb/MONGO_EXPORTER_USERNAME
/stayops/prod/mongodb/MONGO_EXPORTER_PASSWORD
/stayops/prod/mongodb/MONGO_KEYFILE_B64

/stayops/prod/redis/LOKI_URL

/stayops/prod/mock-ota/MOCK_OTA_IMAGE
/stayops/prod/mock-ota/MOCK_OTA_DOMAIN
/stayops/prod/mock-ota/MOCK_OTA_PMS_WEBHOOK_URL
/stayops/prod/mock-ota/MOCK_OTA_HTPASSWD_B64
/stayops/prod/mock-ota/LOKI_URL

/stayops/prod/observability/GRAFANA_PASSWORD

/stayops/minimal/app/API_DOMAIN
/stayops/minimal/app/SPRING_MONGODB_URI
/stayops/minimal/app/TOSS_SECRET_KEY
/stayops/minimal/app/MOCK_OTA_ENDPOINT
/stayops/minimal/app/MOCK_OTA_PMS_WEBHOOK_URL
/stayops/minimal/app/MOCK_OTA_HTPASSWD_B64
/stayops/minimal/app/GHCR_USERNAME
/stayops/minimal/app/GHCR_TOKEN

/stayops/minimal/mongodb/MONGO_REPLICA_SET
/stayops/minimal/mongodb/MONGO_HOST
/stayops/minimal/mongodb/MONGO_INITDB_ROOT_USERNAME
/stayops/minimal/mongodb/MONGO_INITDB_ROOT_PASSWORD
/stayops/minimal/mongodb/MONGO_APP_USERNAME
/stayops/minimal/mongodb/MONGO_APP_PASSWORD
/stayops/minimal/mongodb/MONGO_EXPORTER_USERNAME
/stayops/minimal/mongodb/MONGO_EXPORTER_PASSWORD
```

`MONGO_KEYFILE_B64`와 `MOCK_OTA_HTPASSWD_B64`는 파일 내용을 base64로 인코딩한 값이다.
배포 스크립트가 EC2에서 각각 `mongo-keyfile`, `.htpasswd` 파일로 복원한다.

### minimal Parameter Store 수동 입력값

minimal 구성은 production과 다른 prefix를 사용한다. `/stayops/prod/*` 값을 재사용하지 않고
아래 값을 `/stayops/minimal/*` 아래에 직접 입력한다.

#### `/stayops/minimal/app/*`

| Key | Type | Value |
| --- | --- | --- |
| `/stayops/minimal/app/API_DOMAIN` | `String` | `api.learniverse.store` 또는 minimal app에 연결할 public API domain |
| `/stayops/minimal/app/SPRING_MONGODB_URI` | `SecureString` | `mongodb://stayops_app:<MONGO_APP_PASSWORD>@minimal-mongo.stayops.internal:27017/stayops?replicaSet=rs0&directConnection=true&retryWrites=true&authSource=admin` |
| `/stayops/minimal/app/TOSS_SECRET_KEY` | `SecureString` | 사용할 Toss secret key |
| `/stayops/minimal/app/MOCK_OTA_ENDPOINT` | `String` | `http://mock-ota-app:8081` |
| `/stayops/minimal/app/MOCK_OTA_PMS_WEBHOOK_URL` | `String` | `http://app:8080/api/v1/properties/{propertyId}/channels/webhook/{channelCode}` |
| `/stayops/minimal/app/MOCK_OTA_HTPASSWD_B64` | `SecureString` | `.htpasswd` 파일 내용을 base64로 인코딩한 값 |

`SPRING_MONGODB_URI`의 `<MONGO_APP_PASSWORD>`는
`/stayops/minimal/mongodb/MONGO_APP_PASSWORD`와 같은 값이어야 한다.

GHCR image가 private이면 다음 값을 추가한다. public image이면 생략할 수 있다.

| Key | Type | Value |
| --- | --- | --- |
| `/stayops/minimal/app/GHCR_USERNAME` | `String` | GHCR 사용자명 |
| `/stayops/minimal/app/GHCR_TOKEN` | `SecureString` | GHCR pull 권한 token |

#### `/stayops/minimal/mongodb/*`

| Key | Type | Value |
| --- | --- | --- |
| `/stayops/minimal/mongodb/MONGO_REPLICA_SET` | `String` | `rs0` |
| `/stayops/minimal/mongodb/MONGO_HOST` | `String` | `minimal-mongo.stayops.internal` |
| `/stayops/minimal/mongodb/MONGO_INITDB_ROOT_USERNAME` | `String` | `root` |
| `/stayops/minimal/mongodb/MONGO_INITDB_ROOT_PASSWORD` | `SecureString` | 직접 생성한 root password |
| `/stayops/minimal/mongodb/MONGO_APP_USERNAME` | `String` | `stayops_app` |
| `/stayops/minimal/mongodb/MONGO_APP_PASSWORD` | `SecureString` | 직접 생성한 app password |
| `/stayops/minimal/mongodb/MONGO_EXPORTER_USERNAME` | `String` | `stayops_exporter` |
| `/stayops/minimal/mongodb/MONGO_EXPORTER_PASSWORD` | `SecureString` | 직접 생성한 exporter password |

minimal app은 Mock OTA와 같은 Docker Compose network 안에서 실행된다. 그래서
`MOCK_OTA_ENDPOINT`와 `MOCK_OTA_PMS_WEBHOOK_URL`은 public domain으로 우회하지 않고
컨테이너 DNS 이름을 사용한다.

## minimal 전환 절차

현재 서버를 유지할 필요가 없다면 다음 순서로 전환한다.

1. GitHub Actions `Terraform Destroy`에서 `topology=production`,
   `confirm=destroy-production`으로 production 리소스를 삭제한다.
2. 위 minimal Parameter Store 값을 수동 입력한다.
3. GitHub Actions `Terraform`에서 `topology=minimal`로 apply한다.
4. GitHub Actions `Bootstrap Deploy`에서 `minimal-mongodb`, `minimal-app`을 배포한다.
   최초 MongoDB 구성 시에는 Mongo bootstrap을 함께 실행한다.
5. minimal app EIP 또는 연결한 domain으로 `/actuator/health`를 확인한다.
6. public DNS를 minimal app EIP로 전환한다.

production과 minimal은 같은 Terraform root를 쓰지만 state key가 다르다. 같은 state에서
`deployment_topology` 값만 바꿔 apply하지 않는다.

## production Apply 이후 실행 순서

production Terraform apply 후 다음 순서로 진행한다.

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

SSM 자동화 사용 시에는 다음 순서로 대체할 수 있다.

```text
1. Parameter Store에 역할별 값을 등록한다.
2. Terraform apply로 artifact bucket과 EC2 IAM 권한을 반영한다.
3. GitHub Actions -> Bootstrap Deploy -> deploy_target=all 실행
4. 필요하면 deploy_target=app만 다시 실행해 App image를 재배포한다.
```

## 참고 문서

- Terraform AWS `aws_instance` user_data: https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/instance
- AWS EC2 user data: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/user-data.html
- Docker restart policies: https://docs.docker.com/engine/containers/start-containers-automatically/
- Docker Compose service restart: https://docs.docker.com/reference/compose-file/services/#restart
- AWS Systems Manager Run Command: https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html
- AWS Systems Manager State Manager: https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-state.html
