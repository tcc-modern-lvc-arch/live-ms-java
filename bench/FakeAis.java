import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executors;

/**
 * Fake AIS Friends API. Usage: java FakeAis.java [port=443] [vessels=2000] [keystore.p12 (password "changeit"; HTTPS if given)].
 * Serves a fixed JSON array on /vessels/bounding-box. HTTPS is needed because the Spring Feign URL
 * (https://www.aisfriends.com) is hard-coded in @FeignClient and cannot be overridden by configuration.
 */
void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 443;
    int n = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
    var rnd = new Random(42);
    var sb = new StringBuilder("[");
    for (int i = 0; i < n; i++) {
        if (i > 0) sb.append(',');
        long mmsi = 710000000L + i;
        sb.append(String.format(java.util.Locale.ROOT,
            "{\"id\":%d,\"vessel_id\":%d,\"vesselClass\":\"A\",\"imo\":%d,\"mmsi\":%d,\"name\":\"VESSEL %d\",\"name_ais\":\"VESSEL %d AIS\","
            + "\"ship_type_id\":%d,\"detailed_type_id\":%d,\"timestamp_of_position\":%d,\"length\":%d,\"beam\":%d,\"to_bow\":%d,\"to_stern\":%d,"
            + "\"to_port\":%d,\"to_starboard\":%d,\"true_heading\":%d,\"course_over_ground\":%.1f,\"speed_over_ground\":%.1f,\"draught\":%.1f,"
            + "\"navigational_status_id\":%d,\"flag\":\"BR\",\"latitude\":%.5f,\"longitude\":%.5f,\"lat_grid\":%d,\"lon_grid\":%d,\"unknown_extra\":\"x\"}",
            i + 1, i + 1000, 9000000L + i, mmsi, i, i, 70 + rnd.nextInt(10), 700 + rnd.nextInt(50), 1760000000L + i,
            50 + rnd.nextInt(250), 10 + rnd.nextInt(40), 20, 30, 5, 5, rnd.nextInt(360), rnd.nextDouble() * 360, rnd.nextDouble() * 20,
            3 + rnd.nextDouble() * 10, rnd.nextInt(8), -28.5 + rnd.nextDouble() * 6, -49.0 + rnd.nextDouble() * 14,
            rnd.nextInt(100), rnd.nextInt(100)));
    }
    byte[] body = sb.append(']').toString().getBytes(StandardCharsets.UTF_8);
    com.sun.net.httpserver.HttpServer server;
    if (args.length > 2) {
        var ks = java.security.KeyStore.getInstance("PKCS12");
        try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[2]))) { ks.load(in, "changeit".toCharArray()); }
        var kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        var ctx = javax.net.ssl.SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        var https = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(port), 0);
        https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ctx));
        server = https;
    } else {
        server = HttpServer.create(new InetSocketAddress(port), 0);
    }
    server.createContext("/vessels/bounding-box", ex -> {
        ex.getRequestBody().readAllBytes();
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (var os = ex.getResponseBody()) { os.write(body); }
    });
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    System.out.println("FakeAis listening on " + port + " vessels=" + n + " bytes=" + body.length);
    Thread.currentThread().join();
}
