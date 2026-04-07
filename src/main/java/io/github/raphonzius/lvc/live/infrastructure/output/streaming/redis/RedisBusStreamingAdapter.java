package io.github.raphonzius.lvc.live.infrastructure.output.streaming.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import io.github.raphonzius.lvc.live.domain.streaming.BusStreamingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis streaming adapter for bus position data.
 * Publishes SPTrans OlhoVivo vehicle positions to the "buses:stream" Redis stream.
 *
 * Location: infrastructure/output/streaming/redis/
 * This is an OUTPUT ADAPTER — publishes data to Redis for downstream consumers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBusStreamingAdapter implements BusStreamingPort {

    private static final String REDIS_STREAM_KEY = "buses:stream";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${streaming.max-len:1000}")
    private int streamMaxLen;

    @Override
    public void publishPositions(VehiclePosition.PositionResponse positions) {
        if (positions == null) {
            return;
        }

        // Use record pattern matching to safely extract vehicle count
        int vehicleCount = switch (positions) {
            case VehiclePosition.PositionResponse(var hr, var lines, var vs) when lines != null ->
                    lines.stream().mapToInt(l -> l.vs() != null ? l.vs().size() : 0).sum();
            case VehiclePosition.PositionResponse(var hr, var lines, var vs) when vs != null ->
                    vs.size();
            default -> 0;
        };

        try {
            String json = objectMapper.writeValueAsString(positions);
            Map<String, String> record = new HashMap<>();
            record.put("data", json);
            record.put("hr", positions.hr());

            redisTemplate.opsForStream().add(REDIS_STREAM_KEY, record);
            redisTemplate.opsForStream().trim(REDIS_STREAM_KEY, streamMaxLen, true);
            log.debug("Published bus positions to Redis stream — {} vehicles, hr={}", vehicleCount, positions.hr());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize bus positions for streaming", e);
        }
    }
}
