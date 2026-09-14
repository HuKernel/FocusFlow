package com.focusflow.core

/** Pure state transitions: the repository commits each returned run atomically. */
class TimerEngine(private val clock: FocusClock) {
    private fun delta(run: FocusRun): Long = if (run.bootId == clock.bootId())
        (clock.monotonicMillis() - run.anchor.monotonicMillis).coerceAtLeast(0)
    else (clock.epochMillis() - run.anchor.epochMillis).coerceAtLeast(0)

    fun elapsed(run: FocusRun): Long {
        val moving = run.anchor.state == TimerState.FOCUSING || run.anchor.state == TimerState.BREAKING
        val value = run.anchor.elapsedBeforeAnchor + if (moving) delta(run) else 0
        return if (run.session.type == TimerType.COUNTDOWN || run.anchor.state == TimerState.BREAKING)
            value.coerceAtMost(run.anchor.plannedDuration) else value
    }

    fun remaining(run: FocusRun): Long = (run.anchor.plannedDuration - elapsed(run)).coerceAtLeast(0)

    fun start(session: FocusSession, taskTitle: String, breakDuration: Long = 5 * 60_000): FocusRun {
        require(session.plannedDuration in 1L..86_400_000L || session.type == TimerType.STOPWATCH && session.plannedDuration == 0L)
        require(breakDuration in 1L..86_400_000L) { "休息时长需为 1 分钟到 24 小时" }
        return FocusRun(session, TimerAnchor(session.id, TimerState.FOCUSING, clock.epochMillis(),
            clock.monotonicMillis(), session.plannedDuration), clock.bootId(), taskTitle, breakDuration)
    }

    private fun anchor(run: FocusRun, state: TimerState, elapsed: Long, paused: Long = run.anchor.pausedDuration): FocusRun =
        run.copy(anchor = run.anchor.copy(state = state, epochMillis = clock.epochMillis(), monotonicMillis = clock.monotonicMillis(),
            elapsedBeforeAnchor = elapsed, pausedDuration = paused), bootId = clock.bootId())

    fun recover(run: FocusRun): FocusRun {
        if (run.anchor.state == TimerState.FOCUSING && run.session.type == TimerType.COUNTDOWN && remaining(run) == 0L)
            return finish(run, false).copy(recoveredWithWallClock = run.recoveredWithWallClock || run.bootId != clock.bootId())
        if (run.anchor.state == TimerState.BREAKING && remaining(run) == 0L)
            return anchor(run, TimerState.SESSION_FINISHED, run.anchor.plannedDuration)
        var restored = run
        if (run.bootId != clock.bootId() && run.anchor.state in listOf(TimerState.FOCUSING, TimerState.PAUSED, TimerState.BREAKING)) {
            val paused = run.anchor.pausedDuration + if (run.anchor.state == TimerState.PAUSED) delta(run) else 0
            restored = anchor(run, run.anchor.state, elapsed(run), paused).copy(
                session = run.session.copy(pausedDuration = paused), recoveredWithWallClock = true)
        }
        return restored
    }

    fun pause(run: FocusRun): FocusRun {
        val current = recover(run)
        if (current.anchor.state == TimerState.FOCUS_COMPLETED) return current
        require(current.anchor.state == TimerState.FOCUSING) { "当前会话不能暂停" }
        val elapsed = elapsed(current)
        return anchor(current, TimerState.PAUSED, elapsed).copy(session = current.session.copy(
            status = SessionStatus.PAUSED, actualDuration = elapsed, updatedAt = clock.epochMillis()))
    }

    fun resume(run: FocusRun): FocusRun {
        val current = recover(run)
        require(current.anchor.state == TimerState.PAUSED) { "当前会话不能继续" }
        val paused = current.anchor.pausedDuration + delta(current)
        return anchor(current, TimerState.FOCUSING, current.anchor.elapsedBeforeAnchor, paused).copy(
            session = current.session.copy(status = SessionStatus.ACTIVE, pausedDuration = paused, updatedAt = clock.epochMillis()))
    }

    fun complete(run: FocusRun): FocusRun {
        val current = recover(run)
        if (current.anchor.state == TimerState.FOCUS_COMPLETED) return current
        require(current.anchor.state in listOf(TimerState.FOCUSING, TimerState.PAUSED)) { "当前会话已结束" }
        require(current.session.type == TimerType.STOPWATCH) { "倒计时尚未结束，可以暂停或取消" }
        return finish(current, false)
    }

    fun cancel(run: FocusRun): FocusRun {
        val current = recover(run)
        if (current.anchor.state == TimerState.FOCUS_COMPLETED) return current
        require(current.anchor.state in listOf(TimerState.FOCUSING, TimerState.PAUSED)) { "当前会话已结束" }
        return finish(current, true)
    }

    private fun finish(run: FocusRun, cancelled: Boolean): FocusRun {
        val elapsed = elapsed(run)
        val paused = run.anchor.pausedDuration + if (run.anchor.state == TimerState.PAUSED) delta(run) else 0
        // Attribute a delayed countdown settlement to its actual deadline, not app reopen time.
        val overdue = if (!cancelled && run.session.type == TimerType.COUNTDOWN && run.anchor.state == TimerState.FOCUSING)
            (run.anchor.elapsedBeforeAnchor + delta(run) - run.anchor.plannedDuration).coerceAtLeast(0) else 0
        val endedAt = (clock.epochMillis() - overdue).coerceAtLeast(run.session.startedAt)
        return anchor(run, if (cancelled) TimerState.CANCELLED else TimerState.FOCUS_COMPLETED, elapsed, paused).copy(
            session = run.session.copy(actualDuration = elapsed, pausedDuration = paused, endedAt = endedAt,
                status = if (cancelled) SessionStatus.CANCELLED else SessionStatus.COMPLETED,
                updatedAt = clock.epochMillis(), revision = run.session.revision + 1))
    }

    fun startBreak(run: FocusRun): FocusRun {
        require(run.anchor.state == TimerState.FOCUS_COMPLETED) { "专注完成后才能开始休息" }
        return anchor(run, TimerState.BREAKING, 0).let { it.copy(anchor = it.anchor.copy(plannedDuration = run.breakDuration)) }
    }

    fun skipBreak(run: FocusRun): FocusRun {
        require(run.anchor.state == TimerState.BREAKING) { "当前不在休息中" }
        return anchor(run, TimerState.SESSION_FINISHED, elapsed(run))
    }
}
