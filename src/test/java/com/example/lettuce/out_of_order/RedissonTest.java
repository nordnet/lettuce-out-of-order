package com.example.lettuce.out_of_order;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RedissonTest {


    private static final Logger log = LoggerFactory.getLogger(RedissonTest.class);

    @Container
    private static final GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:6-alpine")).withExposedPorts(6379);

    @Test
    void noMismatchWhenThrowingErrors() throws InterruptedException {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());

        RedissonClient redissonClient = Redisson.create(config);
        try (ExecutorService executorService = Executors.newFixedThreadPool(8);) {
            AtomicInteger failures = new AtomicInteger(0);

            Runnable task = () -> IntStream.range(0, 100).forEach(i -> {
                String id = String.valueOf(i);
                setAndThrowErrorSometimes(redissonClient, id);
                getAndVerify(redissonClient, id, failures);
            });

            IntStream.range(0, 100).forEach(i -> executorService.submit(task));

            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.MINUTES);

            assertEquals(0, failures.get());
        }
    }


    private void getAndVerify(RedissonClient redissonClient, String id, AtomicInteger failures) {
        redissonClient.reactive().getBucket(id)
                .get().doOnNext(result -> {
                    if (!id.equals(result)) {
                        failures.getAndIncrement();
                        log.error("Result mismatch!!! Expected {} but got {}", id, result);
                    }
                }).subscribe();

    }

    private void setAndThrowErrorSometimes(RedissonClient redissonClient, String id) {
        redissonClient.reactive().getBucket(id).set(id)
                .doOnNext(_ -> {
                    if (new SecureRandom().nextBoolean()) {
                        throw new OutOfMemoryError("Error");
                    }
                }).subscribe();
    }

}
