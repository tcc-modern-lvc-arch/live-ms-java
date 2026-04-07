package io.github.raphonzius.lvc.live.infrastructure.output.streaming.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raphonzius.lvc.live.domain.streaming.VesselStreamingPort;
import io.github.raphonzius.lvc.live.domain.vessel.Vessel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Redis streaming adapter implementing the StreamingPort interface.
 * Publishes vessel data to Redis streams for subscription by other microservices.
 *
 * Location: infrastructure/output/streaming/redis/
 * This is an OUTPUT ADAPTER - publishes data to external system (Redis)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisVesselStreamingAdapter implements VesselStreamingPort {

    private static final String REDIS_STREAM_KEY = "vessels:stream";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${streaming.max-len:1000}")
    private int streamMaxLen;

    @Override
    public void publishVessel(Vessel vessel) {
        try {
            String vesselJson = objectMapper.writeValueAsString(vessel);
            Map<String, String> record = new HashMap<>();
            record.put("data", vesselJson);

            redisTemplate.opsForStream().add(REDIS_STREAM_KEY, record);
            redisTemplate.opsForStream().trim(REDIS_STREAM_KEY, streamMaxLen, true);
            log.debug("Published vessel {} to Redis stream", vessel.id());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize vessel for streaming", e);
        }
    }

    @Override
    public void publishVesselBatch(Stream<? extends Vessel> vesselStream) {
        vesselStream.forEach(this::publishVessel);
    }
}

