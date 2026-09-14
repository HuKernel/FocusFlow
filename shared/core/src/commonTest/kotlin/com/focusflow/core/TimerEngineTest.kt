package com.focusflow.core

import kotlin.test.*

class TimerEngineTest {
    private class Clock : FocusClock {
        var epoch = 1_000_000L
        var monotonic = 100_000L
        var boot = "boot-one"
        override fun epochMillis() = epoch
        override fun monotonicMillis() = monotonic
        override fun bootId() = boot
        fun advance(millis: Long) { epoch += millis; monotonic += millis }
    }
    private val clock = Clock()
    private val engine = TimerEngine(clock)
    private fun start(type: TimerType = TimerType.COUNTDOWN, duration: Long = 60_000) = engine.start(
        FocusSession("session", "user", "task", "phone", "phone", type, duration, startedAt = clock.epoch), "Read")

    @Test fun pauseResumeAndCompleteUseElapsedTime() {
        var run = start()
        clock.advance(20_000)
        run = engine.pause(run)
        clock.advance(30_000)
        assertEquals(20_000L, engine.elapsed(run))
        run = engine.resume(run)
        clock.advance(40_000)
        run = engine.recover(run)
        assertEquals(SessionStatus.COMPLETED, run.session.status)
        assertEquals(60_000L, run.session.actualDuration)
        assertEquals(30_000L, run.session.pausedDuration)
    }

    @Test fun systemClockChangesDoNotChangeSameBootDuration() {
        val run = start()
        clock.monotonic += 20_000
        clock.epoch -= 86_400_000
        assertEquals(40_000L, engine.remaining(run))
        clock.epoch += 2 * 86_400_000
        assertEquals(40_000L, engine.remaining(run))
    }

    @Test fun newEngineRecoversProcessDeathAndAttributesLateCompletionToDeadline() {
        val run = start()
        clock.advance(180_000)
        val restored = TimerEngine(clock).recover(run)
        assertEquals(60_000L, restored.session.actualDuration)
        assertEquals(run.session.startedAt + 60_000, restored.session.endedAt)
        assertEquals(restored, engine.recover(restored))
    }

    @Test fun bootChangeUsesEpochAndReanchorsWithoutRepeatingTime() {
        val run = start()
        clock.epoch += 20_000
        clock.monotonic = 100
        clock.boot = "boot-two"
        val recovered = engine.recover(run)
        assertTrue(recovered.recoveredWithWallClock)
        assertEquals(20_000L, engine.elapsed(recovered))
        clock.advance(10_000)
        assertEquals(30_000L, engine.elapsed(recovered))
        assertEquals(30_000L, engine.remaining(recovered))
    }

    @Test fun pausedRecoveryDoesNotCountBackgroundAsFocus() {
        clock.advance(10_000)
        var run = engine.pause(start(TimerType.STOPWATCH, 0))
        clock.epoch += 60_000
        clock.boot = "boot-two"
        clock.monotonic = 0
        run = engine.recover(run)
        clock.advance(30_000)
        run = engine.resume(run)
        clock.advance(10_000)
        val completed = engine.complete(run)
        assertEquals(10_000L, completed.session.actualDuration)
        assertEquals(90_000L, completed.session.pausedDuration)
    }

    @Test fun cancelPreservesSpentTimeButDoesNotComplete() {
        val run = start()
        clock.advance(15_000)
        val cancelled = engine.cancel(run)
        assertEquals(SessionStatus.CANCELLED, cancelled.session.status)
        assertEquals(15_000L, cancelled.session.actualDuration)
        assertEquals(0L, completedFocusMillis("task", listOf(cancelled.session)))
        assertFailsWith<IllegalArgumentException> { engine.complete(run) }
    }

    @Test fun breakDoesNotModifyCompletedSession() {
        val started = start()
        clock.advance(60_000)
        val completed = engine.recover(started)
        val breaking = engine.startBreak(completed)
        clock.advance(300_000)
        val finished = engine.recover(breaking)
        assertEquals(TimerState.SESSION_FINISHED, finished.anchor.state)
        assertEquals(completed.session, finished.session)
    }

    @Test fun customBreakDurationDrivesBreakPhase() {
        val started = engine.start(FocusSession("session", "user", "task", "phone", "phone",
            plannedDuration = 60_000, startedAt = clock.epoch), "Read", breakDuration = 120_000)
        assertEquals(120_000L, started.breakDuration)
        clock.advance(60_000)
        val breaking = engine.startBreak(engine.recover(started))
        assertEquals(120_000L, breaking.anchor.plannedDuration)
        clock.advance(120_000)
        assertEquals(TimerState.SESSION_FINISHED, engine.recover(breaking).anchor.state)
    }
}
