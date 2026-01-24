package com.slack.bot.context;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

@Slf4j
public class CleanupExecutionListener extends AbstractTestExecutionListener implements Ordered {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (isNotIntegrationTest(testContext)) {
            return;
        }

        cleanupWithSql(testContext);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isNotIntegrationTest(TestContext testContext) {
        return AnnotationUtils.findAnnotation(testContext.getTestClass(), SpringBootTest.class) == null;
    }

    private void cleanupWithSql(TestContext testContext) {
        DataSource dataSource = testContext.getApplicationContext().getBean(DataSource.class);
        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();

        resourceDatabasePopulator.addScript(new ClassPathResource("sql/cleanup.sql"));
        resourceDatabasePopulator.execute(dataSource);
    }
}
