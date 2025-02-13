package com.example.lettuce.out_of_order;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class PureLettuceSetupTest {


    private static final Logger log = LoggerFactory.getLogger(PureLettuceSetupTest.class);

    @Container
    private static final GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:6-alpine")).withExposedPorts(6379);

    @Test
    void errorSimulation() throws InterruptedException {

        try (
                ExecutorService executorService = Executors.newFixedThreadPool(8);
                RedisClient redisClient = RedisClient.create(RedisURI.create(redis.getHost(), redis.getFirstMappedPort()));
                StatefulRedisConnection<String, String> connection = redisClient.connect()) {

            AtomicInteger failures = new AtomicInteger(0);

            Runnable task = () -> IntStream.range(0, 100).forEach(i -> {
                String id = String.valueOf(i);
                setAndThrowErrorSometimes(connection, id);
                getAndVerify(connection, id, failures);
            });

            IntStream.range(0, 100).forEach(i -> executorService.submit(task));

            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.MINUTES);

            assertEquals(0, failures.get());
        }
    }

    @Test
    void errorSimulationSynchronousStart() throws InterruptedException {

        CountDownLatch synchronousStartLatch = new CountDownLatch(1);
        //CountDownLatch awaitFailureLatch = new CountDownLatch(1);

        try (
                ExecutorService executorService = Executors.newFixedThreadPool(8);
                RedisClient redisClient = RedisClient.create(RedisURI.create(redis.getHost(), redis.getFirstMappedPort()));
                StatefulRedisConnection<String, String> connection = redisClient.connect()) {

            AtomicInteger failures = new AtomicInteger(0);

            Runnable task = () -> IntStream.range(0, 100).forEach(i -> {
                try {
                    synchronousStartLatch.await();
                } catch (InterruptedException e) {
                }
                String id = String.valueOf(i);
                setAndThrowErrorSometimes(connection, id);
                getAndVerify(connection, id, failures);
            });

            IntStream.range(0, 100).forEach(i -> executorService.submit(task));

            synchronousStartLatch.countDown();

            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.MINUTES);

            assertEquals(0, failures.get());
        }
    }

    private void getAndVerify(StatefulRedisConnection<String, String> connection, String id, AtomicInteger failures) {
        connection.reactive().get(id)
                .doOnNext(result -> {
                    if (!id.equals(result)) {
                        failures.getAndIncrement();
                        log.error("Result mismatch!!! Expected {} but got {}", id, result);
                    }
                }).subscribe();
    }

    private void setAndThrowErrorSometimes(StatefulRedisConnection<String, String> connection, String id) {
        connection.reactive()
                .set(id, id)
                .doOnNext(_ -> {
                    if (new SecureRandom().nextBoolean()) {
                        throw new OutOfMemoryError("pubsub error");
                    }
                }).subscribe();
    }

}
