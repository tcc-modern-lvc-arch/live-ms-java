package io.github.raphonzius.lvc.live.domain.template;

/**
 * Template Guide for Adding a New API (e.g., Weather, Aviation)
 *
 * Follow these steps to add a new data source to the Live Adapter:
 */

/*
 * STEP 1: Create Domain Model (domain/weather/WeatherData.java)
 *
 * Use sealed classes and records for type safety and immutability.
 * Keep it independent - no external dependencies.
 */

// public sealed class Weather permits WeatherRecord {
//     public abstract String location();
//     public abstract double temperature();
//     public abstract long timestamp();
//
//     public record WeatherRecord(
//         String location,
//         double temperature,
//         String condition,
//         long timestamp,
//         double humidity,
//         double windSpeed
//     ) implements Weather {
//         @Override
//         public String location() { return location; }
//
//         @Override
//         public double temperature() { return temperature; }
//
//         @Override
//         public long timestamp() { return timestamp; }
//     }
// }

/*
 * STEP 2: Create Port Interface (domain/weather/WeatherPort.java)
 *
 * Define the contract for your data source.
 * This is what the domain expects from infrastructure.
 */

// public interface WeatherPort {
//     Weather.WeatherRecord fetchWeather(String city);
//     List<Weather.WeatherRecord> fetchMultipleCities(List<String> cities);
// }

/*
 * STEP 3: Create Feign Client (infrastructure/external/weather/WeatherFeignClient.java)
 *
 * Use Spring Cloud OpenFeign to define HTTP calls.
 * Example: OpenWeatherMap API
 */

// @FeignClient(
//     name = "weather-api",
//     url = "https://api.openweathermap.org",
//     configuration = WeatherFeignConfiguration.class
// )
// public interface WeatherFeignClient {
//     @GetMapping("/data/2.5/weather")
//     WeatherResponse getWeather(
//         @RequestParam("q") String city,
//         @RequestParam("appid") String apiKey
//     );
// }

/*
 * STEP 4: Create Feign Configuration (infrastructure/external/weather/WeatherFeignConfiguration.java)
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
 * STEP 5: Create Error Decoder (infrastructure/external/weather/WeatherErrorDecoder.java)
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
 * STEP 6: Create Infrastructure Adapter (infrastructure/external/weather/WeatherAdapter.java)
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
//     public Weather.WeatherRecord fetchWeather(String city) {
//         var response = weatherFeignClient.getWeather(city, apiKey);
//         return mapToWeatherRecord(response);
//     }
//
//     public Weather.WeatherRecord fetchWeatherFallback(String city, Exception ex) {
//         log.warn("Weather API unavailable for city: {}", city);
//         return null; // or return cached data
//     }
// }

/*
 * STEP 7: Create Application Service (application/service/WeatherService.java)
 *
 * Orchestrates between domain logic and infrastructure.
 * Coordinates with streaming layer.
 */

// @Service
// public class WeatherService {
//     private final WeatherPort weatherPort;
//     private final StreamingPort streamingPort;
//
//     public void fetchAndStreamWeather(List<String> cities) {
//         cities.stream()
//             .map(weatherPort::fetchWeather)
//             .forEach(streamingPort::publishWeather);
//     }
// }

/*
 * STEP 8: Create Scheduler (infrastructure/scheduler/WeatherPollingScheduler.java)
 *
 * Configure periodic polling of the API.
 */

// @Component
// public class WeatherPollingScheduler {
//     private final WeatherService weatherService;
//
//     @Scheduled(fixedDelayString = "${weather.polling.interval:60000}")
//     public void pollWeather() {
//         weatherService.fetchAndStreamWeather(
//             List.of("São Paulo", "Rio de Janeiro", "Salvador")
//         );
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
//     public ResponseEntity<Weather.WeatherRecord> getWeather(@PathVariable String city) {
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
 * ✅ Easy to swap implementations (Redis → Kafka, etc.)
 * ✅ Consistent error handling and resilience
 * ✅ Clear separation of concerns
 */

