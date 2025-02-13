package com.example.lettuce.out_of_order;

import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RedisIncidentApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(RedisIncidentApplicationTest.class);

    @SuppressWarnings("rawtypes")
    @Container
    private static final GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:6-alpine")).withExposedPorts(6379);

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    @DynamicPropertySource
    private static void setRedisProperties(DynamicPropertyRegistry registry) {
        String address = redis.getHost();
        Integer port = redis.getFirstMappedPort();
        registry.add("redis.host", () -> address);
        registry.add("redis.port", () -> port);
        registry.add("redis.readHost", () -> address);
        registry.add("redis.readPort", () -> port);
        log.info("**** Redis address: {}, port: {} ****", address, port);
    }

    private LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate = new ReactiveStringRedisTemplate(lettuceConnectionFactory);

    private void saveSession(ReactiveStringRedisTemplate redisTemplate, String id) {
        redisTemplate.opsForValue().set(id, id)
                .doOnNext(_ -> {
                    if (new SecureRandom().nextBoolean()) {
                        throw new OutOfMemoryError("pubsub error");
                    }
                }).subscribe();
    }

    private Mono<String> getSession(ReactiveStringRedisTemplate redisTemplate, String id) {
        return redisTemplate.opsForValue().get(id)
                .doOnNext(result -> {
                    log.info("Got result: {} with id {}", result, id);
                });
    }

    @BeforeEach
    void setUp() {
        lettuceConnectionFactory.start();
    }

    @Test
    void errorSimulation() throws InterruptedException {
        Runnable task = () -> IntStream.range(0, 100).forEach(i -> {
            String id = String.valueOf(i);
            saveSession(reactiveStringRedisTemplate, id);
            getSession(reactiveStringRedisTemplate, id).doOnNext(result -> {
                if (!id.equals(result)) {
                    log.error("Result mismatch!!! Expected {} but got {}", id, result);
                }
            }).subscribe();
        });

        IntStream.range(0, 100).forEach(i -> executorService.submit(task));

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

    }

    @Test
    void noErrorWhenNoSharedConnection() throws InterruptedException {
        Runnable task = () -> IntStream.range(0, 100).forEach(i -> {
            String id = String.valueOf(i);
            saveSession(reactiveStringRedisTemplate, id);
            getSession(reactiveStringRedisTemplate, id).doOnNext(result -> {
                if (!id.equals(result)) {
                    log.error("Result mismatch!!! Expected {} but got {}", id, result);
                }
            }).subscribe();
        });

        IntStream.range(0, 100).forEach(i -> executorService.submit(task));

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

    }

}
