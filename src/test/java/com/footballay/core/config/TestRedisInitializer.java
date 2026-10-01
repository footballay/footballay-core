package com.footballay.core.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.utility.DockerImageName;

public class TestRedisInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.4.5-alpine"))
                    .withReuse(true);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!context.getEnvironment().getProperty("footballay.test.redis.enabled", Boolean.class, true)) {
            return;
        }
        synchronized (REDIS) {
            if (!REDIS.isRunning()) {
                REDIS.start();
            }
        }
        TestPropertyValues.of(
                "spring.data.redis.host=" + REDIS.getHost(),
                "spring.data.redis.port=" + REDIS.getFirstMappedPort(),
                "spring.data.redis.password=1234"
        ).applyTo(context.getEnvironment());
    }
}
