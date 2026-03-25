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
    private final boolean autoStartup;

    private volatile boolean running;
    private Thread workerThread;

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
        this(runnerName, pollOperation, adaptivePollingBackoff, pollingSleeper, errorDelay, true);
    }

    AdaptivePollingRunner(
            String runnerName,
            IntSupplier pollOperation,
            AdaptivePollingBackoff adaptivePollingBackoff,
            PollingSleeper pollingSleeper,
            Duration errorDelay,
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

        this.runnerName = runnerName;
        this.pollOperation = pollOperation;
        this.adaptivePollingBackoff = adaptivePollingBackoff;
        this.pollingSleeper = pollingSleeper;
        this.errorDelay = errorDelay;
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
    public void start() {
        if (running) {
            return;
        }

        running = true;
        adaptivePollingBackoff.reset();
        workerThread = new Thread(this::runLoop, buildThreadName());
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        pollingSleeper.wakeUp();
        Thread threadToJoin = workerThread;
        workerThread = null;
        if (threadToJoin == null || Thread.currentThread() == threadToJoin) {
            return;
        }

        try {
            threadToJoin.join(Duration.ofSeconds(5L).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
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
        while (running) {
            runSingleCycle();
        }
    }

    private String buildThreadName() {
        return "adaptive-poller-" + runnerName.replace(' ', '-').replace('_', '-');
    }

    private enum PollCycleOutcome {
        WORK_FOUND,
        EMPTY,
        ERROR
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
