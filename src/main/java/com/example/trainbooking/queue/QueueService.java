package com.example.trainbooking.queue;

import com.example.trainbooking.config.TrafficControlProperties;
import com.example.trainbooking.dto.QueueStatusResponse;
import com.example.trainbooking.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String BOOKING_QUEUE = "booking_queue";
    private static final String ADMITTED_SET = "booking_admitted";

    private final StringRedisTemplate redisTemplate;
    private final TrafficControlProperties properties;

    public void enqueueIfAbsent(String userId) {
        Long size = redisTemplate.opsForZSet().size(BOOKING_QUEUE);
        if (size != null && size >= properties.queue().maxQueueSize()) {
            throw new ApiException("Virtual waiting room is full. Please retry in a few seconds.");
        }
        Double score = redisTemplate.opsForZSet().score(BOOKING_QUEUE, userId);
        if (score == null) {
            redisTemplate.opsForZSet().add(BOOKING_QUEUE, userId, Instant.now().toEpochMilli());
            log.debug("User {} added to queue", userId);
        }
    }

    public QueueStatusResponse getStatus(String userId) {
        Long rank = redisTemplate.opsForZSet().rank(BOOKING_QUEUE, userId);
        boolean admitted = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ADMITTED_SET, userId));

        if (admitted) {
            return new QueueStatusResponse(userId, 0L, 0L, true);
        }
        if (rank == null) {
            return new QueueStatusResponse(userId, -1L, 0L, false);
        }
        long position = rank + 1;
        long releaseRate = Math.max(1, properties.queue().releasesPerSecond());
        long estimatedWait = Math.max(1, position / releaseRate) * properties.queue().avgServiceSeconds();
        return new QueueStatusResponse(userId, position, estimatedWait, false);
    }

    public boolean isAdmitted(String userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ADMITTED_SET, userId));
    }

    public void consumeAdmission(String userId) {
        redisTemplate.opsForSet().remove(ADMITTED_SET, userId);
    }

    public void releaseUsers() {
        int batchSize = properties.queue().releasesPerSecond();
        Set<String> nextUsers = redisTemplate.opsForZSet().range(BOOKING_QUEUE, 0, batchSize - 1);
        if (nextUsers == null || nextUsers.isEmpty()) {
            return;
        }
        nextUsers.stream().filter(Objects::nonNull).forEach(userId -> {
            redisTemplate.opsForSet().add(ADMITTED_SET, userId);
            redisTemplate.expire(ADMITTED_SET, 1, TimeUnit.MINUTES);
            redisTemplate.opsForZSet().remove(BOOKING_QUEUE, userId);
        });
        log.info("Released {} users from virtual waiting room", nextUsers.size());
    }
}
