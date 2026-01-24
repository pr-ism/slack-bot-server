package com.slack.bot.application;

import com.slack.bot.domain.auth.TokenDecoder;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@MockitoBean(types = {DateTimeProvider.class, TokenDecoder.class})
public @interface IntegrationTest {
}
