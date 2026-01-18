package com.slack.bot.context;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.slack.bot.context.exception.TestContextClassNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.restdocs.ManualRestDocumentation;
import org.springframework.web.bind.annotation.RestController;

public class ControllerMockInjectionSupport implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String BASE_PACKAGE = "com.slack.bot";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) applicationContext;
        registerConfigurationBeans(registry);
        registerControllerSliceTestBeans(registry, applicationContext.getEnvironment());
    }

    private void registerConfigurationBeans(BeanDefinitionRegistry registry) {
        registerBeanDefinition(
                registry,
                "objectMapper",
                ObjectMapper.class,
                createCustomObjectMapper()
        );
        registerBeanDefinition(
                registry,
                "provider",
                ManualRestDocumentation.class,
                new ManualRestDocumentation()
        );
    }

    private ObjectMapper createCustomObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));
        return mapper;
    }

    private void registerControllerSliceTestBeans(BeanDefinitionRegistry registry, Environment environment) {
        Map<Class<?>, Object> serviceMocks = new HashMap<>();

        for (Class<?> controllerClass : findRestControllerClasses(environment)) {
            Map<String, Class<?>> dependencies = analyzeDependencies(controllerClass);

            registerDependencyMocks(registry, dependencies, serviceMocks);
            registerControllerDefinition(registry, controllerClass, dependencies);
        }
    }

    private Map<String, Class<?>> analyzeDependencies(Class<?> controllerClass) {
        Map<String, Class<?>> dependencies = new HashMap<>();
        Constructor<?> constructor = controllerClass.getDeclaredConstructors()[0];

        for (Parameter param : constructor.getParameters()) {
            dependencies.put(param.getName(), param.getType());
        }
        return dependencies;
    }

    private void registerDependencyMocks(
            BeanDefinitionRegistry registry,
            Map<String, Class<?>> dependencies,
            Map<Class<?>, Object> serviceMocks
    ) {
        for (String dependencyName : dependencies.keySet()) {
            Class<?> targetClass = dependencies.get(dependencyName);
            String beanName = getBeanName(targetClass);

            if (!registry.containsBeanDefinition(beanName)) {
                Object mock = serviceMocks.computeIfAbsent(targetClass, k -> mock(targetClass));

                registerBeanDefinition(registry, beanName, targetClass, mock);
            }
        }
    }

    private void registerControllerDefinition(
            BeanDefinitionRegistry registry,
            Class<?> controllerClass,
            Map<String, Class<?>> dependencies
    ) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(controllerClass);

        ConstructorArgumentValues args = new ConstructorArgumentValues();

        for (Parameter parameter : controllerClass.getDeclaredConstructors()[0].getParameters()) {
            Class<?> type = parameter.getType();

            if (dependencies.containsValue(type)) {
                args.addGenericArgumentValue(new RuntimeBeanReference(getBeanName(type)));
            }
        }

        definition.setConstructorArgumentValues(args);
        registry.registerBeanDefinition(getBeanName(controllerClass), definition);
    }

    private String getBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();

        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private void registerBeanDefinition(
            BeanDefinitionRegistry registry,
            String beanName,
            Class<?> targetType,
            Object instance
    ) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(MockServiceFactoryBean.class);

        ConstructorArgumentValues args = new ConstructorArgumentValues();
        args.addGenericArgumentValue(instance);
        args.addGenericArgumentValue(targetType);
        definition.setConstructorArgumentValues(args);

        registry.registerBeanDefinition(beanName, definition);
    }

    private Set<Class<?>> findRestControllerClasses(Environment environment) {
        Set<Class<?>> results = new HashSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.setEnvironment(environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                results.add(Class.forName(beanDefinition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new TestContextClassNotFoundException(e);
            }
        }
        return results;
    }

    public static class MockServiceFactoryBean implements FactoryBean<Object> {

        private final Object instance;
        private final Class<?> targetType;

        public MockServiceFactoryBean(Object instance, Class<?> targetType) {
            this.instance = instance;
            this.targetType = targetType;
        }

        @Override
        public Object getObject() {
            return instance;
        }

        @Override
        public Class<?> getObjectType() {
            return targetType;
        }
    }
}
