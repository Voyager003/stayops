package com.stayops.channel.infrastructure.sync

import com.stayops.channel.application.service.ChannelSyncApplication
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class ChannelAvailabilitySyncAdapterTest : BehaviorSpec({

    val channelSyncApplication = mockk<ChannelSyncApplication>()
    val sut = ChannelAvailabilitySyncAdapter(channelSyncApplication)

    given("가용 재고 동기화 요청이 들어오면") {
        `when`("동기화 task 생성을 요청하면") {
            then("ChannelSyncApplication에 위임한다") {
                justRun {
                    channelSyncApplication.createAvailabilitySyncTasks(
                        "prop-1",
                        "rt-1",
                        LocalDate.of(2026, 4, 12),
                        3
                    )
                }

                sut.requestAvailabilitySync("prop-1", "rt-1", LocalDate.of(2026, 4, 12), 3)

                verify(exactly = 1) {
                    channelSyncApplication.createAvailabilitySyncTasks(
                        "prop-1",
                        "rt-1",
                        LocalDate.of(2026, 4, 12),
                        3
                    )
                }
            }
        }

        `when`("즉시 처리를 요청하면") {
            then("ChannelSyncApplication에 위임한다") {
                justRun { channelSyncApplication.processTasksImmediately("prop-1") }

                sut.processImmediately("prop-1")

                verify(exactly = 1) {
                    channelSyncApplication.processTasksImmediately("prop-1")
                }
            }
        }
    }
})
