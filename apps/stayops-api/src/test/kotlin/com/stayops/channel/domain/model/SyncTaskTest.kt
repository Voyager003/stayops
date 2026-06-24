package com.stayops.channel.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class SyncTaskTest : BehaviorSpec({

    val fixedNow = Instant.parse("2026-03-12T00:00:00Z")
    val samplePayload = mapOf("roomTypeId" to "rt-1", "date" to "2026-03-20", "availableCount" to 3)

    fun pendingTask(
        id: String = "task-1",
        propertyId: String = "prop-1",
        channelCode: String = "AGODA"
    ) = SyncTask.create(
        id = id,
        propertyId = propertyId,
        channelCode = channelCode,
        type = SyncTaskType.AVAILABILITY_UPDATE,
        payload = samplePayload,
        now = fixedNow
    )

    // -- 생성 --

    given("SyncTask 생성 시") {
        `when`("create() 호출하면") {
            val task = pendingTask()

            then("PENDING 상태로 생성된다") {
                task.status shouldBe SyncTaskStatus.PENDING
            }
            then("retryCount가 0이다") {
                task.retryCount shouldBe 0
            }
            then("maxRetries가 3이다") {
                task.maxRetries shouldBe 3
            }
            then("nextRetryAt이 null이다") {
                task.nextRetryAt shouldBe null
            }
            then("idempotencyKey가 생성된다") {
                task.idempotencyKey shouldNotBe ""
            }
            then("payload가 보존된다") {
                task.payload["roomTypeId"] shouldBe "rt-1"
            }
        }
    }

    given("SyncTask 동일성 비교 시") {
        `when`("같은 id의 SyncTask가 서로 다른 처리 상태를 가지면") {
            val pending = pendingTask()
            val processing = pending.startProcessing("worker-a", fixedNow.plusSeconds(60), fixedNow)

            then("같은 작업으로 판단한다") {
                processing shouldBe pending
                processing.hashCode() shouldBe pending.hashCode()
            }
        }

        `when`("id가 다르면 같은 payload의 작업이라도") {
            val first = pendingTask()
            val second = pendingTask(id = "task-2")

            then("다른 작업으로 판단한다") {
                second shouldNotBe first
            }
        }

        `when`("HashSet에 같은 id의 다른 상태 작업을 넣으면") {
            val tasks = hashSetOf(pendingTask())
            tasks += pendingTask().startProcessing("worker-a", fixedNow.plusSeconds(60), fixedNow)

            then("중복 추가되지 않는다") {
                tasks.size shouldBe 1
            }
        }
    }

    // -- 상태 전이: PENDING → IN_PROGRESS --

    given("PENDING 상태의 SyncTask") {
        val task = pendingTask()

        `when`("startProcessing() 호출 시") {
            val processing = task.startProcessing("worker-a", fixedNow.plusSeconds(60), fixedNow)

            then("상태가 IN_PROGRESS로 변경된다") {
                processing.status shouldBe SyncTaskStatus.IN_PROGRESS
            }
            then("lease 소유자와 만료 시각을 기록한다") {
                processing.lockedBy shouldBe "worker-a"
                processing.lockedUntil shouldBe fixedNow.plusSeconds(60)
            }
        }
    }

    given("lease가 만료된 IN_PROGRESS 상태의 SyncTask") {
        val task = pendingTask()
            .startProcessing("worker-a", fixedNow.plusSeconds(60), fixedNow)

        `when`("다른 worker가 만료 이후 startProcessing()을 호출하면") {
            val reclaimed = task.startProcessing("worker-b", fixedNow.plusSeconds(130), fixedNow.plusSeconds(70))

            then("lease를 다시 획득할 수 있다") {
                reclaimed.status shouldBe SyncTaskStatus.IN_PROGRESS
                reclaimed.lockedBy shouldBe "worker-b"
                reclaimed.lockedUntil shouldBe fixedNow.plusSeconds(130)
            }
        }

        `when`("lease가 만료되기 전에 다른 worker가 startProcessing()을 호출하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    task.startProcessing("worker-b", fixedNow.plusSeconds(90), fixedNow.plusSeconds(30))
                }
            }
        }
    }

    // -- 상태 전이: IN_PROGRESS → COMPLETED --

    given("IN_PROGRESS 상태의 SyncTask") {
        val task = pendingTask().startProcessing()

        `when`("complete() 호출 시") {
            val completed = task.complete()

            then("상태가 COMPLETED로 변경된다") {
                completed.status shouldBe SyncTaskStatus.COMPLETED
            }
            then("lease 정보가 제거된다") {
                completed.lockedBy shouldBe null
                completed.lockedUntil shouldBe null
            }
        }
    }

    // -- 상태 전이: IN_PROGRESS → SKIPPED --

    given("IN_PROGRESS 상태의 SyncTask에서 채널/매핑이 없을 때") {
        val task = pendingTask().startProcessing()

        `when`("skip() 호출 시") {
            val skipped = task.skip("채널 또는 connectionInfo 없음")

            then("상태가 SKIPPED로 변경된다") {
                skipped.status shouldBe SyncTaskStatus.SKIPPED
            }
            then("사유가 lastError에 기록된다") {
                skipped.lastError shouldBe "채널 또는 connectionInfo 없음"
            }
            then("nextRetryAt이 null이다") {
                skipped.nextRetryAt shouldBe null
            }
        }
    }

    // -- 상태 전이: IN_PROGRESS → fail --

    given("IN_PROGRESS 상태에서 실패 시") {
        val task = pendingTask().startProcessing()

        `when`("fail() 호출하면") {
            val failed = task.fail("Connection timeout")

            then("retryCount가 증가한다") {
                failed.retryCount shouldBe 1
            }
            then("lastError가 기록된다") {
                failed.lastError shouldBe "Connection timeout"
            }
        }

        `when`("maxRetries 미만이면") {
            val failed = task.fail("Connection timeout")

            then("상태가 PENDING으로 돌아간다") {
                failed.status shouldBe SyncTaskStatus.PENDING
            }
            then("nextRetryAt이 설정된다") {
                failed.nextRetryAt shouldNotBe null
            }
        }

        `when`("maxRetries에 도달하면") {
            var failedTask = task
            var retryAt = fixedNow
            repeat(2) {
                failedTask = failedTask.fail("error", retryAt)
                retryAt = failedTask.nextRetryAt!!
                failedTask = failedTask.startProcessing(retryAt)
            }
            val finalFailed = failedTask.fail("final error", retryAt)

            then("상태가 FAILED로 변경된다") {
                finalFailed.status shouldBe SyncTaskStatus.FAILED
            }
            then("retryCount가 maxRetries와 같다") {
                finalFailed.retryCount shouldBe finalFailed.maxRetries
            }
        }
    }

    // -- 지수 백오프 --

    given("지수 백오프 계산 시") {
        val task = pendingTask().startProcessing()

        `when`("첫 번째 실패 후") {
            val failed1 = task.fail("error", fixedNow)
            then("nextRetryAt이 약 30초 후이다") {
                failed1.nextRetryAt shouldBe fixedNow.plusSeconds(30)
            }
        }

        `when`("두 번째 실패 후") {
            val failed2 = task.fail("error", fixedNow)
                .startProcessing(fixedNow.plusSeconds(30))
                .fail("error", fixedNow.plusSeconds(30))
            then("nextRetryAt이 약 60초 후이다") {
                failed2.nextRetryAt shouldBe fixedNow.plusSeconds(90)
            }
        }
    }

    // -- 수동 재시도 --

    given("FAILED 상태의 SyncTask") {
        var task = pendingTask().startProcessing()
        var retryAt = fixedNow
        repeat(2) {
            task = task.fail("error", retryAt)
            retryAt = task.nextRetryAt!!
            task = task.startProcessing(retryAt)
        }
        val failedTask = task.fail("final error", retryAt)

        `when`("retry() 호출 시") {
            val retried = failedTask.retry()

            then("상태가 PENDING으로 변경된다") {
                retried.status shouldBe SyncTaskStatus.PENDING
            }
            then("retryCount가 0으로 초기화된다") {
                retried.retryCount shouldBe 0
            }
            then("nextRetryAt이 null로 초기화된다") {
                retried.nextRetryAt shouldBe null
            }
            then("lastError가 null로 초기화된다") {
                retried.lastError shouldBe null
            }
        }
    }

    given("FAILED가 아닌 상태의 SyncTask") {
        val task = pendingTask()

        `when`("retry() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    task.retry()
                }
            }
        }
    }
})
