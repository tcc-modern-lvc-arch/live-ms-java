package io.github.raphonzius.lvc.live.infrastructure.output.streaming.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.streaming.FloodingStreamingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis streaming adapter for CGESP flooding data.
 * Publishes flooding points to the {@code floodings:stream} Redis stream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFloodingStreamingAdapter implements FloodingStreamingPort {

    private static final String REDIS_STREAM_KEY = "floodings:stream";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishFloodings(LocalDate date, List<FloodingPoint.FloodData> floodings) {
        if (floodings == null || floodings.isEmpty()) {
            log.debug("No flooding points to publish for date={}", date);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(floodings);
            Map<String, String> record = new HashMap<>();
            record.put("date", date.toString());
            record.put("count", String.valueOf(floodings.size()));
            record.put("data", json);

            redisTemplate.opsForStream().add(REDIS_STREAM_KEY, record);
            log.debug("Published {} flooding point(s) to Redis stream for date={}", floodings.size(), date);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize flooding points for streaming", e);
        }
    }
}
