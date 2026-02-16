package com.slack.bot.context;

import java.util.Map;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

@Slf4j
public class CleanupExecutionListener extends AbstractTestExecutionListener implements Ordered {

    private static final long DRAIN_TIMEOUT_MILLIS = 3_000L;
    private static final long DRAIN_POLL_INTERVAL_MILLIS = 25L;

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (isNotIntegrationTest(testContext)) {
            return;
        }

        drainBackgroundTasks(testContext);
        cleanupWithSql(testContext);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isNotIntegrationTest(TestContext testContext) {
        return AnnotationUtils.findAnnotation(testContext.getTestClass(), SpringBootTest.class) == null;
    }

    private void drainBackgroundTasks(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();

        cancelPendingScheduledTasks(applicationContext);
        waitForAllTaskExecutorsIdle(applicationContext);
        waitForTaskSchedulerIdle(applicationContext);
    }

    private void cancelPendingScheduledTasks(ApplicationContext applicationContext) {
        if (!applicationContext.containsBean("taskScheduler")) {
            return;
        }

        Object bean = applicationContext.getBean("taskScheduler");
        if (!(bean instanceof ThreadPoolTaskScheduler scheduler)) {
            return;
        }

        ScheduledThreadPoolExecutor executor = scheduler.getScheduledThreadPoolExecutor();

        executor.getQueue()
                .forEach(runnable -> {
                    if (runnable instanceof RunnableScheduledFuture<?> scheduledFuture) {
                        try {
                            scheduledFuture.cancel(false);
                        } catch (Exception exception) {
                            log.debug("exception occurred while canceling scheduled task", exception);
                        }
                    }
                });

        executor.purge();
    }

    private void waitForAllTaskExecutorsIdle(ApplicationContext applicationContext) {
        Map<String, ThreadPoolTaskExecutor> executors = applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);

        executors.forEach((beanName, taskExecutor) -> waitForTaskExecutorIdle(beanName, taskExecutor));
    }

    private void waitForTaskExecutorIdle(String beanName, ThreadPoolTaskExecutor taskExecutor) {
        waitUntilIdle(beanName, () -> {
            ThreadPoolExecutor executor = taskExecutor.getThreadPoolExecutor();
            return executor.getActiveCount() == 0 && executor.getQueue().isEmpty();
        });
    }

    private void waitForTaskSchedulerIdle(ApplicationContext applicationContext) {
        if (!applicationContext.containsBean("taskScheduler")) {
            return;
        }

        Object bean = applicationContext.getBean("taskScheduler");
        if (!(bean instanceof ThreadPoolTaskScheduler scheduler)) {
            return;
        }

        waitUntilIdle("taskScheduler", () -> {
            ScheduledThreadPoolExecutor executor = scheduler.getScheduledThreadPoolExecutor();
            if (executor == null) {
                return true;
            }

            return executor.getActiveCount() == 0;
        });
    }

    private void waitUntilIdle(String target, BooleanSupplier condition) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DRAIN_TIMEOUT_MILLIS);

        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }

            sleepSilently(DRAIN_POLL_INTERVAL_MILLIS);
        }

        String exceptionMessage = String.format(
                "Cleanup 실패: 백그라운드 작업이 %dms 내에 종료되지 않았습니다. target=%s",
                DRAIN_TIMEOUT_MILLIS, target
        );

        throw new IllegalStateException(exceptionMessage);
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanupWithSql(TestContext testContext) {
        DataSource dataSource = testContext.getApplicationContext().getBean(DataSource.class);
        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();

        resourceDatabasePopulator.addScript(new ClassPathResource("sql/cleanup.sql"));
        resourceDatabasePopulator.execute(dataSource);
    }
}
