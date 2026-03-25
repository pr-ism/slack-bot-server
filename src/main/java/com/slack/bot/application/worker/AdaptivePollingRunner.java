package com.slack.bot.application.worker;

import java.time.Duration;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

@Slf4j
public class AdaptivePollingRunner implements SmartLifecycle {

    private final String runnerName;
    private final IntSupplier pollOperation;
    private final AdaptivePollingBackoff adaptivePollingBackoff;
    private final PollingSleeper pollingSleeper;
    private final Duration errorDelay;
    private final Duration stopJoinTimeout;
    private final boolean autoStartup;

    private volatile boolean running;
    private Thread workerThread;
    private Runnable stopCompletionCallback;

    public AdaptivePollingRunner(
            String runnerName,
            Duration baseDelay,
            Duration capDelay,
            IntSupplier pollOperation
    ) {
        this(runnerName, baseDelay, capDelay, pollOperation, true);
    }

    public AdaptivePollingRunner(
            String runnerName,
            Duration baseDelay,
            Duration capDelay,
            IntSupplier pollOperation,
            boolean autoStartup
    ) {
        this(
                runnerName,
                pollOperation,
                new AdaptivePollingBackoff(baseDelay, capDelay),
                new MonitorPollingSleeper(),
                baseDelay,
                Duration.ofSeconds(5L),
                autoStartup
        );
    }

    AdaptivePollingRunner(
            String runnerName,
            IntSupplier pollOperation,
            AdaptivePollingBackoff adaptivePollingBackoff,
            PollingSleeper pollingSleeper,
            Duration errorDelay
    ) {
        this(
                runnerName,
                pollOperation,
                adaptivePollingBackoff,
                pollingSleeper,
                errorDelay,
                Duration.ofSeconds(5L),
                true
        );
    }

    AdaptivePollingRunner(
            String runnerName,
            IntSupplier pollOperation,
            AdaptivePollingBackoff adaptivePollingBackoff,
            PollingSleeper pollingSleeper,
            Duration errorDelay,
            Duration stopJoinTimeout,
            boolean autoStartup
    ) {
        if (runnerName == null || runnerName.isBlank()) {
            throw new IllegalArgumentException("runnerName은 비어 있을 수 없습니다.");
        }
        if (pollOperation == null) {
            throw new IllegalArgumentException("pollOperation은 null일 수 없습니다.");
        }
        if (adaptivePollingBackoff == null) {
            throw new IllegalArgumentException("adaptivePollingBackoff는 null일 수 없습니다.");
        }
        if (pollingSleeper == null) {
            throw new IllegalArgumentException("pollingSleeper는 null일 수 없습니다.");
        }
        if (errorDelay == null || errorDelay.toMillis() <= 0L) {
            throw new IllegalArgumentException("errorDelay는 0보다 커야 합니다.");
        }
        if (stopJoinTimeout == null || stopJoinTimeout.isNegative()) {
            throw new IllegalArgumentException("stopJoinTimeout은 0 이상이어야 합니다.");
        }

        this.runnerName = runnerName;
        this.pollOperation = pollOperation;
        this.adaptivePollingBackoff = adaptivePollingBackoff;
        this.pollingSleeper = pollingSleeper;
        this.errorDelay = errorDelay;
        this.stopJoinTimeout = stopJoinTimeout;
        this.autoStartup = autoStartup;
    }

    void runSingleCycle() {
        PollCycleOutcome pollCycleOutcome = pollOnce();
        if (pollCycleOutcome == PollCycleOutcome.WORK_FOUND) {
            adaptivePollingBackoff.reset();
            return;
        }
        if (pollCycleOutcome == PollCycleOutcome.EMPTY) {
            sleep(adaptivePollingBackoff.nextDelayAfterEmptyPoll());
            return;
        }

        sleep(errorDelay);
    }

    public void wakeUp() {
        pollingSleeper.wakeUp();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (workerThread != null) {
            if (workerThread.isAlive()) {
                throw new IllegalStateException("이전 poller 스레드가 아직 종료되지 않았습니다.");
            }
            workerThread = null;
        }

        running = true;
        workerThread = new Thread(() -> runLoop(), buildThreadName());
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void stop() {
        StopRequest stopRequest = prepareStop(null);
        awaitThreadTermination(stopRequest.threadToJoin());
    }

    @Override
    public void stop(Runnable callback) {
        StopRequest stopRequest = prepareStop(callback);
        if (stopRequest.isCompletedImmediately()) {
            runStopCompletionCallback();
            return;
        }
        if (awaitThreadTermination(stopRequest.threadToJoin())) {
            runStopCompletionCallback();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private PollCycleOutcome pollOnce() {
        try {
            int claimedCount = pollOperation.getAsInt();
            if (claimedCount > 0) {
                return PollCycleOutcome.WORK_FOUND;
            }

            return PollCycleOutcome.EMPTY;
        } catch (Exception e) {
            log.error("{} 실행에 실패했습니다.", runnerName, e);
            return PollCycleOutcome.ERROR;
        }
    }

    private void sleep(Duration delay) {
        try {
            pollingSleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runLoop() {
        Runnable callbackToRun = null;
        try {
            adaptivePollingBackoff.reset();
            while (running) {
                runSingleCycle();
            }
        } finally {
            adaptivePollingBackoff.releaseOwnership();
            synchronized (this) {
                if (workerThread == Thread.currentThread()) {
                    workerThread = null;
                    if (!running) {
                        callbackToRun = stopCompletionCallback;
                        stopCompletionCallback = null;
                    }
                }
            }
        }
        if (callbackToRun != null) {
            callbackToRun.run();
        }
    }

    private String buildThreadName() {
        return "adaptive-poller-" + runnerName.replace(' ', '-').replace('_', '-');
    }

    private StopRequest prepareStop(Runnable callback) {
        synchronized (this) {
            if (callback != null) {
                registerStopCompletionCallback(callback);
            }

            Thread threadToJoin = workerThread;
            boolean workerAlive = threadToJoin != null && threadToJoin.isAlive();
            if (!running) {
                if (!workerAlive) {
                    return StopRequest.completed();
                }
                if (Thread.currentThread() == threadToJoin) {
                    return StopRequest.deferred();
                }
                return StopRequest.awaitTermination(threadToJoin);
            }

            running = false;
            pollingSleeper.wakeUp();
            if (!workerAlive) {
                return StopRequest.completed();
            }
            if (Thread.currentThread() == threadToJoin) {
                return StopRequest.deferred();
            }
            return StopRequest.awaitTermination(threadToJoin);
        }
    }

    private boolean awaitThreadTermination(Thread threadToJoin) {
        if (threadToJoin == null) {
            return false;
        }

        try {
            threadToJoin.join(stopJoinTimeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (threadToJoin.isAlive()) {
            log.warn("{} 중지 대기 시간이 초과되었습니다.", runnerName);
            return false;
        }

        return true;
    }

    private synchronized void registerStopCompletionCallback(Runnable callback) {
        if (stopCompletionCallback == null) {
            stopCompletionCallback = callback;
            return;
        }

        Runnable existingCallback = stopCompletionCallback;
        stopCompletionCallback = () -> {
            existingCallback.run();
            callback.run();
        };
    }

    private void runStopCompletionCallback() {
        Runnable callbackToRun;
        synchronized (this) {
            callbackToRun = stopCompletionCallback;
            stopCompletionCallback = null;
        }
        if (callbackToRun != null) {
            callbackToRun.run();
        }
    }

    private enum PollCycleOutcome {
        WORK_FOUND,
        EMPTY,
        ERROR
    }

    private record StopRequest(Thread threadToJoin, boolean completedImmediately) {

        private boolean isCompletedImmediately() {
            return completedImmediately;
        }

        private static StopRequest completed() {
            return new StopRequest(null, true);
        }

        private static StopRequest deferred() {
            return new StopRequest(null, false);
        }

        private static StopRequest awaitTermination(Thread threadToJoin) {
            return new StopRequest(threadToJoin, false);
        }
    }

    interface PollingSleeper {
        PollingSleepResult sleep(Duration delay) throws InterruptedException;

        void wakeUp();
    }

    enum PollingSleepResult {
        COMPLETED,
        WOKEN_UP
    }

    static final class MonitorPollingSleeper implements PollingSleeper {

        private final Object monitor = new Object();
        private boolean wakeUpRequested;

        @Override
        public PollingSleepResult sleep(Duration delay) throws InterruptedException {
            long remainingMillis = delay.toMillis();
            long deadlineMillis = System.currentTimeMillis() + remainingMillis;

            synchronized (monitor) {
                if (wakeUpRequested) {
                    wakeUpRequested = false;
                    return PollingSleepResult.WOKEN_UP;
                }

                while (!wakeUpRequested && remainingMillis > 0L) {
                    monitor.wait(remainingMillis);
                    remainingMillis = deadlineMillis - System.currentTimeMillis();
                }

                if (wakeUpRequested) {
                    wakeUpRequested = false;
                    return PollingSleepResult.WOKEN_UP;
                }

                return PollingSleepResult.COMPLETED;
            }
        }

        @Override
        public void wakeUp() {
            synchronized (monitor) {
                wakeUpRequested = true;
                monitor.notifyAll();
            }
        }
    }
}
