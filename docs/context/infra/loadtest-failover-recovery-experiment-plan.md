# MongoDB Failover / Recovery Experiment Plan

## Status

- Status: parked
- Reason: out of current scope

## Why parked

현재 부하 테스트 단계는 **true standalone MongoDB** 기준이다. 따라서 아래 가설은 지금 검증할 수 없다.

- primary down -> secondary election
- app restart 없이 write 정상화
- 죽었던 노드의 secondary 복귀
- oplog catch-up 이후 정합성 확인

이 가설들은 replica set 전제가 있어야만 의미가 있다.

## Current replacement

현재 단계의 기준 문서는 아래 두 개다.

- `docs/context/infra/aws-mongodb-loadtest-plan.md`
- `docs/context/infra/aws-mongodb-loadtest-runbook.md`

핵심 방향은:

1. standalone read-heavy 검증
2. production-like browsing pattern
3. hot-property concentration
4. write / failover는 다음 단계로 분리

## Reopen conditions

아래 조건 중 하나가 충족되면 이 문서를 다시 활성화한다.

1. MongoDB를 single-node replica set으로 전환
2. MongoDB를 multi-node replica set으로 전환
3. reservation write-path를 replica set 전제에서 다시 검증 가능한 상태로 정리

## Next document shape when reopened

재개 시 이 문서는 아래 항목을 반드시 포함해야 한다.

- failover 가설
- recovery 가설
- write failure window 정의
- app reconnect 관찰 포인트
- replica set health / election evidence 수집 방법
