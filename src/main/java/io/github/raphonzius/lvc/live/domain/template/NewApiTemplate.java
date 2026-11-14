package io.github.raphonzius.lvc.live.domain.template;

/**
 * Template Guide for Adding a New API (e.g., Weather, Aviation)
 *
 * Follow these steps to add a new data source to the Live Adapter:
 */

/*
 * STEP 1: Create Domain Model (domain/weather/Weather.java)
 *
 * Sealed interface + record, Java names, only the fields the service uses.
 * The external wire format (snake_case JSON, extra fields) does NOT belong here — see STEP 3b.
 */

// public sealed interface Weather permits Weather.WeatherData {
//     String location();
//     double temperature();
//     long timestamp();
//
//     record WeatherData(String location, double temperature, Double humidity, long timestamp)
//             implements Weather {}
// }

/*
 * STEP 2: Create Port Interface (domain/weather/WeatherPort.java)
 *
 * Define the contract for your data source.
 * This is what the domain expects from infrastructure.
 */

// public interface WeatherPort {
//     Weather.WeatherData fetchWeather(String city);
//     List<Weather.WeatherData> fetchMultipleCities(List<String> cities);
// }

/*
 * STEP 3: Create Feign Client (infrastructure/input/external/weather/WeatherFeignClient.java)
 *
 * Returns the wire DTO from STEP 3b, never the domain record.
 */

// @FeignClient(name = "weather-api", url = "https://api.openweathermap.org",
//              configuration = WeatherFeignConfiguration.class)
// public interface WeatherFeignClient {
//     @GetMapping("/data/2.5/weather")
//     WeatherResponse getWeather(@RequestParam("q") String city, @RequestParam("appid") String apiKey);
// }

/*
 * STEP 3b: Create the wire DTO (infrastructure/input/external/weather/WeatherResponse.java)
 *
 * Anti-corruption layer on the input side: external names via @JsonProperty
 * (com.fasterxml.jackson.annotation works with Jackson 2 and 3), translation in toDomain().
 * Reference: AisVesselResponse.
 */

// @JsonIgnoreProperties(ignoreUnknown = true)
// public record WeatherResponse(
//         @JsonProperty("name") String name,
//         @JsonProperty("main_temp") double mainTemp,
//         @JsonProperty("main_humidity") Double mainHumidity,
//         @JsonProperty("dt") long dt
// ) {
//     public Weather.WeatherData toDomain() {
//         return new Weather.WeatherData(name, mainTemp, mainHumidity, dt);
//     }
// }

/*
 * STEP 4: Create Feign Configuration (infrastructure/input/external/weather/WeatherFeignConfiguration.java)
 *
 * Configure timeouts, error handling, and resilience patterns.
 */

// @Configuration
// public class WeatherFeignConfiguration {
//     @Bean
//     Logger.Level feignLoggerLevel() {
//         return Logger.Level.BASIC;
//     }
//
//     @Bean
//     public ErrorDecoder errorDecoder() {
//         return new WeatherErrorDecoder();
//     }
// }

/*
 * STEP 5: Create Error Decoder (infrastructure/input/external/weather/WeatherErrorDecoder.java)
 *
 * Handle API-specific errors gracefully.
 */

// public class WeatherErrorDecoder implements ErrorDecoder {
//     @Override
//     public Exception decode(String methodKey, Response response) {
//         return switch (response.status()) {
//             case 404 -> new WeatherApiException("City not found", response.status());
//             case 429 -> new WeatherApiException("Rate limit exceeded", response.status());
//             case 500, 502, 503 -> new WeatherApiException("Weather API error", response.status());
//             default -> new Default().decode(methodKey, response);
//         };
//     }
// }

/*
 * STEP 6: Create Infrastructure Adapter (infrastructure/input/external/weather/WeatherAdapter.java)
 *
 * Implements the port interface using the Feign client.
 * Add @CircuitBreaker annotation for fault tolerance.
 */

// @Component
// public class WeatherAdapter implements WeatherPort {
//     private final WeatherFeignClient weatherFeignClient;
//     private final String apiKey; // From config
//
//     public WeatherAdapter(WeatherFeignClient weatherFeignClient,
//                          @Value("${weather.api.key}") String apiKey) {
//         this.weatherFeignClient = weatherFeignClient;
//         this.apiKey = apiKey;
//     }
//
//     @Override
//     @CircuitBreaker(
//         name = "weather-api",
//         fallbackMethod = "fetchWeatherFallback"
//     )
//     public Weather.WeatherData fetchWeather(String city) {
//         return weatherFeignClient.getWeather(city, apiKey).toDomain();
//     }
//
//     public Weather.WeatherData fetchWeatherFallback(String city, Exception ex) {
//         log.warn("Weather API unavailable for city: {}", city);
//         return null; // or return cached data
//     }
// }

/*
 * STEP 7: Create Application Service (application/service/WeatherService.java)
 *
 * Orchestrates fetch -> publish through ports only. No try/catch/logging boilerplate:
 * the polling runner (STEP 8) owns that.
 */

// @Service
// @RequiredArgsConstructor
// public class WeatherService {
//     private final WeatherPort weatherPort;
//     private final WeatherStreamingPort weatherStreamingPort;   // domain/streaming/
//
//     public void fetchAndStreamWeather(List<String> cities) {
//         weatherStreamingPort.publishWeather(cities.stream().map(weatherPort::fetchWeather).toList());
//     }
// }

/*
 * STEP 7b: Map to the event-hub contract and publish (infrastructure/output/grpc/)
 *
 * 1. Add a pure mapping method to EventRequestMapper (anti-corruption layer, unit-test it
 *    with a fixed Clock, including null optional fields), e.g.:
 *
 *      public EventRequest toEvent(Weather.WeatherData data) {
 *          var payload = RainAreaPayload.newBuilder()...;
 *          return envelope("craam", EntityType.RAIN_AREA, data.location()).setRainArea(payload).build();
 *      }
 *
 * 2. The streaming adapter is transport only — EventPublisher handles virtual threads,
 *    the concurrency limit, per-call deadline, failure counting and the batch summary log:
 */

// @Component
// @RequiredArgsConstructor
// public class GrpcWeatherStreamingAdapter implements WeatherStreamingPort {
//     private final EventRequestMapper mapper;
//     private final EventPublisher publisher;
//
//     @Override
//     public void publishWeather(List<Weather.WeatherData> data) {
//         publisher.publish("weather", data.stream().map(mapper::toEvent).toList());
//     }
// }

/*
 * STEP 8: Create Scheduler (infrastructure/scheduler/WeatherPollingScheduler.java)
 *
 * Implement PollingJob (Strategy) and delegate to PollingJobRunner, which binds a
 * correlation id (ScopedValue + MDC), logs, records the lvc.polling.duration timer
 * (tags job/outcome) and swallows failures so the schedule keeps running.
 */

// @Component
// @RequiredArgsConstructor
// public class WeatherPollingScheduler implements PollingJob {
//     private final PollingJobRunner runner;
//     private final WeatherService weatherService;
//
//     @Scheduled(fixedDelayString = "${weather.polling.interval:60000}")
//     public void pollWeather() {
//         runner.run(this);
//     }
//
//     @Override public String name() { return "weather"; }
//
//     @Override public void poll() {
//         weatherService.fetchAndStreamWeather(List.of("São Paulo", "Rio de Janeiro", "Salvador"));
//     }
// }

/*
 * STEP 9: Create REST Controller (infrastructure/adapter/web/WeatherController.java)
 *
 * Expose HTTP endpoints for external access.
 */

// @RestController
// @RequestMapping("/api/v1/weather")
// public class WeatherController {
//     @GetMapping("/{city}")
//     public ResponseEntity<Weather.WeatherData> getWeather(@PathVariable String city) {
//         var weather = weatherPort.fetchWeather(city);
//         return ResponseEntity.ok(weather);
//     }
// }

/*
 * STEP 10: Update Configuration (application.yaml)
 *
 * Add configuration for the new API.
 */

// weather:
//   api:
//     key: your-api-key-here
//   polling:
//     interval: 60000  # 60 seconds
//
// resilience4j:
//   circuitbreaker:
//     instances:
//       weather-api:
//         slidingWindowSize: 10
//         minimumNumberOfCalls: 5
//         failureRateThreshold: 50
//         waitDurationInOpenState: 30000ms

/*
 * ARCHITECTURE COMPARISON
 *
 * AIS (Current):
 *   └─ Single endpoint, polling every 30s, Brazil coast bounding box
 *
 * Weather (Template):
 *   └─ Multiple endpoints, polling every 60s, configurable cities
 *
 * Aviation (Future):
 *   └─ Real-time data, WebSocket or polling, global coverage
 *
 * All follow the SAME hexagonal architecture pattern!
 */

/*
 * BENEFITS OF THIS APPROACH
 *
 * ✅ Domain logic is independent
 * ✅ Easy to add new APIs without touching existing code
 * ✅ Easy to test (mock implementations via ports)
 * ✅ Easy to swap implementations (gRPC → Kafka, etc.)
 * ✅ Consistent error handling and resilience
 * ✅ Clear separation of concerns
 */

