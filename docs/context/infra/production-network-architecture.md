# 프로덕션 네트워크 아키텍처 검토

> 작성일: 2026-04-05
> 상태: 검토 중 (미적용)

---

## 배경

현재 StayOps는 단일 EC2 인스턴스에 모든 서비스를 Docker Compose로 운영 중이다.
프로덕션 레벨의 네트워크 분리(Private Subnet, NAT Gateway, Bastion Host)를 적용했을 때의 구조 변화와,
비용을 고려한 대안(OCI Always Free + Cloudflare)을 정리한다.

---

## 1. 현재 인프라 구성

### 구조

```
AWS EC2 (단일 인스턴스, Public IP 직접 노출)
└── Docker Compose (stayops-net bridge)
     ├── nginx        — 리버스 프록시 (:80, :443)
     ├── app           — Spring Boot (stayops)
     ├── mock-ota      — Mock OTA 서버
     ├── mongodb       — Mongo 8 (Replica Set)
     ├── redis         — Redis 7
     ├── loki          — 로그 저장
     ├── promtail      — Docker 로그 수집 → Loki 전송
     └── grafana       — 로그 대시보드 (:3001)
```

### CI/CD

```
main push → GitHub Actions (Test → Build → GHCR Push) → SSH로 EC2 접속 → deploy.sh
```

### 특징

- 모든 서비스가 단일 EC2의 Docker 컨테이너로 실행
- MongoDB, Redis는 Docker 내부 네트워크에서만 통신 (prod 기준 외부 포트 미노출)
- SSL은 Let's Encrypt + Nginx에서 처리
- **SPOF**: EC2 1대 장애 시 전체 서비스 다운

---

## 2. Private Subnet + NAT Gateway를 사용하는 이유

### 핵심: 공격 표면(Attack Surface) 최소화

```
Internet
   │
   ▼
┌──────────────┐
│ Public Subnet │  ← ALB/Nginx만 배치 (외부 트래픽 수신)
└──────┬───────┘
       │ (내부 통신만 허용)
       ▼
┌──────────────┐
│Private Subnet │  ← App Server, DB, Redis 배치
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  NAT Gateway  │  ← Private 서버가 외부로 나갈 때만 사용
└──────────────┘
```

| 이유 | 설명 |
|---|---|
| DB 직접 접근 차단 | MongoDB, Redis가 Public IP를 갖지 않으므로 인터넷에서 직접 접속 불가 |
| Security Group 계층화 | Public SG → 80/443만, Private SG → Public에서 오는 트래픽만 허용 |
| 아웃바운드 제어 | Private 서버의 외부 API 호출(Toss Payments 등)만 NAT 경유, 인바운드 원천 차단 |
| 규정 준수 | PCI-DSS, ISMS 등 결제/개인정보 처리 시 네트워크 분리 필수 |
| Blast Radius 제한 | Public 서버 침해 시에도 Private으로의 lateral movement를 ACL로 차단 |

**NAT Gateway 역할**: Private 서버가 OS 패치, 외부 API 호출 시 사용하는 단방향 게이트. 나가는 건 허용, 들어오는 건 차단.

---

## 3. Bastion Host

Private Subnet 서버에 SSH 접근하기 위한 유일한 진입점. "Jump Server"라고도 부른다.

### 동작 방식

```
개발자 PC
    │ SSH (22번)
    ▼
┌─────────────────────────────┐
│ Public Subnet               │
│  ┌──────────────┐           │
│  │ Bastion Host │           │
│  │ SG: 회사 IP만 22번 허용  │
│  └──────┬───────┘           │
└─────────┼───────────────────┘
          │ SSH (내부 IP)
          ▼
┌─────────────────────────────┐
│ Private Subnet              │
│  App / MongoDB / Redis      │
│  SG: Bastion SG에서만 22번  │
└─────────────────────────────┘
```

### 접속 방법

```bash
# ProxyJump 사용 (한 줄)
ssh -J ec2-user@bastion-public-ip ec2-user@10.0.2.10

# ~/.ssh/config 설정
Host bastion
    HostName 52.xx.xx.xx
    User ec2-user

Host app-server
    HostName 10.0.2.10
    User ec2-user
    ProxyJump bastion
```

### 효과

- SSH 공격 표면이 Bastion 1대로 수렴
- Bastion 로그만 감사하면 전체 접근 이력 파악 가능
- 비상시 Bastion SG 차단 = 전체 SSH 즉시 격리

---

## 4. 프로덕션 레벨 구성도

```
사용자 → Route 53 → CloudFront (CDN + WAF)
                         │
    ┌────────────────────────────────────────────┐
    │              AWS VPC (10.0.0.0/16)         │
    │                                            │
    │  [Public Subnet]                           │
    │   ALB (Multi-AZ) + Bastion Host            │
    │        │                                   │
    │  [Private Subnet - App Layer]              │
    │   Auto Scaling Group (EC2 x 2+, AZ 분산)   │
    │   각 EC2: App Container + Promtail         │
    │        │                                   │
    │        │ NAT Gateway (아웃바운드 전용)       │
    │        │                                   │
    │  [Private Subnet - Data Layer]             │
    │   MongoDB Replica Set (3대, Multi-AZ)      │
    │   ElastiCache Redis (Primary + Replica)    │
    │                                            │
    │  [Private Subnet - Observability]          │
    │   Loki (S3 저장) + Grafana                 │
    └────────────────────────────────────────────┘
```

### 현재 vs 프로덕션 비교

| 항목 | 현재 | 프로덕션 레벨 |
|---|---|---|
| 네트워크 | EC2 Public IP 직접 노출 | VPC + Public/Private Subnet 분리 |
| 로드밸런싱 | Nginx (단일 컨테이너) | ALB (Multi-AZ, 헬스체크, 자동 장애 전환) |
| App Server | EC2 1대 | Auto Scaling Group (최소 2대, AZ 분산) |
| MongoDB | 컨테이너 1개 | EC2 3대 Replica Set (Primary + Secondary + Arbiter) |
| Redis | 컨테이너 1개 | ElastiCache (Primary + Replica, Multi-AZ) |
| SSH 접근 | EC2 직접 접속 | Bastion Host 경유만 허용 |
| DNS | IP 직접 또는 수동 | Route 53 (헬스체크 + 페일오버) |
| CDN/보안 | 없음 | CloudFront + WAF |
| 아웃바운드 | EC2 직접 인터넷 | NAT Gateway 경유 |
| 가용성 | SPOF (1대 죽으면 전체 다운) | Multi-AZ (1개 AZ 장애에도 서비스 유지) |
| 로그 저장 | Loki 로컬 볼륨 | Loki + S3 (영구 저장) |

### 예상 비용 (AWS 기준)

| 항목 | 월 예상 비용 |
|---|---|
| ALB | ~$16 |
| NAT Gateway | ~$32 + 트래픽 |
| EC2 x 2 (App) | ~$16 (t3.micro) |
| EC2 x 3 (MongoDB) | ~$24 |
| ElastiCache | ~$13 (t3.micro) |
| **합계** | **~$100+/월** |

---

## 5. 비용 절감 대안: OCI Always Free + Cloudflare

AWS 비용이 부담되는 초기 단계에서 무료 클라우드 조합으로 동등한 보안 수준을 달성할 수 있다.

### OCI Always Free 할당량

| 유형 | 무료 범위 |
|---|---|
| AMD Micro | VM.Standard.E2.1.Micro (1 OCPU / 1GB) x **2대** |
| ARM A1 | 총 **4 OCPU / 24GB RAM** (인스턴스 수 자유 분할) |

### 추천 구성: ARM A1 3분할

```
인스턴스 1 (ARM A1) — 2 OCPU / 8GB  → App Server (Spring Boot)
인스턴스 2 (ARM A1) — 1 OCPU / 8GB  → MongoDB
인스턴스 3 (ARM A1) — 1 OCPU / 8GB  → Redis + Loki + Promtail
────────────────────────────────────
합계                  4 OCPU / 24GB  → 무료 한도 내
```

### 아키텍처

```
사용자 → Cloudflare (DNS + CDN + WAF, 무료)
            │
            │ Cloudflare Tunnel (Public IP 비노출)
            ▼
    OCI VCN (Private Subnet 활용 가능)
     ├── VM 1: Spring Boot (:8080, localhost만 바인딩)
     ├── VM 2: MongoDB (:27017, 내부 통신만)
     └── VM 3: Redis + Loki + Promtail
```

### 보안 (서브넷 분리 대체)

```bash
# Cloudflare Tunnel로 Public IP 노출 자체 제거
cloudflared tunnel --url http://localhost:8080

# 모든 서비스 127.0.0.1에만 바인딩
# OS 방화벽으로 MongoDB/Redis 외부 접근 차단
```

### 비용 비교

| 항목 | AWS (최소 구성) | OCI + Cloudflare |
|---|---|---|
| VM | ~$8/월 | $0 |
| NAT Gateway | ~$32/월 | 불필요 (Tunnel) |
| ALB | ~$16/월 | 불필요 (Cloudflare) |
| SSL | ACM (ALB 필요) | Cloudflare 자동 |
| **합계** | **~$56+/월** | **$0/월** |

### 주의사항

- OCI Always Free VM은 idle 상태가 길면 회수될 수 있음 (cron으로 활동 유지)
- 단일 VM이므로 HA 포기 — 사이드 프로젝트/초기 스타트업에 적합
- 트래픽 증가 시 유료 티어 또는 AWS 마이그레이션 필요

---

## 결론

| 단계 | 권장 구성 | 비용 |
|---|---|---|
| 개발/사이드 프로젝트 | 현재 구성 (단일 EC2 + Docker Compose) | ~$8/월 |
| 비용 최적화 | OCI Always Free + Cloudflare Tunnel | $0/월 |
| 프로덕션 전환 | VPC + Subnet 분리 + ALB + Multi-AZ | ~$100+/월 |

단계적 전환이 현실적이며, 현재 Docker Compose 기반 구조는 어떤 환경으로든 마이그레이션이 용이하다.
