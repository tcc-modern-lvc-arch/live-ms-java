package bench;

import com.sun.net.httpserver.HttpServer;
import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake EventHub. gRPC on args[0] (default 50051); HTTP stats on args[1] (default 8081):
 *   GET /stats  -> {"count":N,"first_ns":T,"last_ns":T}  (System.nanoTime based, same JVM clock)
 *   POST /reset -> zeroes counters
 */
public final class FakeEventHub {
    static final AtomicLong COUNT = new AtomicLong();
    static volatile long first = 0, last = 0;

    public static void main(String[] args) throws Exception {
        int grpcPort = args.length > 0 ? Integer.parseInt(args[0]) : 50051;
        int httpPort = args.length > 1 ? Integer.parseInt(args[1]) : 8081;
        var svc = new EventHubGrpc.EventHubImplBase() {
            @Override
            public void sendEvent(EventRequest req, StreamObserver<EventAck> out) {
                long now = System.nanoTime();
                long c = COUNT.incrementAndGet();
                if (c == 1) first = now;
                last = now;
                out.onNext(EventAck.newBuilder().setEventId(Long.toString(c)).setStatus("accepted").build());
                out.onCompleted();
            }
        };
        Server grpc = ServerBuilder.forPort(grpcPort).addService(svc).build().start();
        var http = HttpServer.create(new InetSocketAddress(httpPort), 0);
        http.createContext("/stats", ex -> {
            byte[] b = ("{\"count\":" + COUNT.get() + ",\"first_ns\":" + first + ",\"last_ns\":" + last + "}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (var os = ex.getResponseBody()) { os.write(b); }
        });
        http.createContext("/reset", ex -> {
            COUNT.set(0); first = 0; last = 0;
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        http.start();
        System.out.println("FakeEventHub grpc=" + grpcPort + " http=" + httpPort);
        grpc.awaitTermination();
    }
}
