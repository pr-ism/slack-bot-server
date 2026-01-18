package com.slack.bot.context;

import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class ResetMockTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();

        for (String beanDefinitionName : beanDefinitionNames) {
            Object bean = applicationContext.getBean(beanDefinitionName);
            if (Mockito.mockingDetails(bean).isMock()) {
                Mockito.reset(bean);
            }
        }
    }
}
