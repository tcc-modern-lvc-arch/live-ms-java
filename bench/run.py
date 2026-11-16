#!/usr/bin/env python3
"""AIS-slice benchmark harness: Spring Boot vs Quarkus. See README.md.

Usage: JAVA_HOME=<jdk25> taskset -c 2,3 python3 bench/run.py [--startup-reps 7] [--load-runs 5] [--polls 20]
                                                              [--variants a,b,...] [--out bench/results]
Apps are pinned to CPUs 0,1 (APP_CPUS); fakes and this orchestrator should be pinned to the other CPUs.
"""
import argparse, csv, ssl, http.client, json, os, re, signal, statistics, subprocess, sys, time
from pathlib import Path

BENCH = Path(__file__).resolve().parent
WORK = Path(os.environ.get("WORK_DIR", BENCH / "work"))
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if "JAVA_HOME" in os.environ else "java"
APP_CPUS = os.environ.get("APP_CPUS", "0,1")
FAKE_CPUS = os.environ.get("FAKE_CPUS", "2,3")
AIS_PORT, HUB_GRPC, HUB_HTTP, APP_PORT = 443, 50051, 8081, 8080
N_VESSELS = 2000
JVM = ["-XX:+UseG1GC", "-XX:+UseCompactObjectHeaders", "-Xmx512m"]
ENV = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}

# Only AIS should run: other pollers get huge intervals (their first fixed-delay run still fires once at
# startup; their hard-coded hosts resolve to 127.0.0.1 via the hosts file and fail the TLS handshake fast).
# The Spring Feign URLs are hard-coded in @FeignClient (config properties cannot override them), so the fake AIS
# is served over HTTPS on :443 and www.aisfriends.com is mapped to 127.0.0.1 with -Djdk.net.hosts.file.
TLS = [f"-Djavax.net.ssl.trustStore={WORK}/ais-trust.p12", "-Djavax.net.ssl.trustStorePassword=changeit",
       "-Djavax.net.ssl.trustStoreType=PKCS12"]
SPRING_PROPS = [
    "-Dspring.cloud.vault.enabled=false",
    "-Dais.polling.interval=86400000", "-Dcgesp.polling.interval=86400000", "-Dolhovivo.polling.interval=86400000",
    f"-Djdk.net.hosts.file={WORK}/hosts", *TLS,
    "-Dlogging.level.io.github.raphonzius.lvc=INFO", "-Dlogging.level.org.springframework.cloud.openfeign=INFO",
]
QUARKUS_PROPS = [
    "-Dais.polling.interval=24h",
    "-Dquarkus.rest-client.ais-api.url=https://127.0.0.1",
    '-Dquarkus.log.category."io.github.raphonzius.lvc".level=INFO', *TLS,
]
SPRING_HEALTH = "/actuator/health/readiness"
QUARKUS_HEALTH = "/q/health/ready"
SPRING_POLL = ("/api/vessels/poll", {"X-API-Version": "1.0"}, 202)
QUARKUS_POLL = ("/api/v1/vessels/poll", {}, 200)

def spring_cmd(extra_jvm=(), jar=None, cwd=None):
    return dict(cmd=[JAVA, *JVM, *extra_jvm, *SPRING_PROPS, "-jar", jar], cwd=cwd)

VARIANTS = {
    "spring-jvm":      dict(**spring_cmd(jar=str(WORK / "live-ms-java.jar")), health=SPRING_HEALTH, poll=SPRING_POLL,
                            fw=r"Started \w+ in ([\d.]+) seconds", art=WORK / "live-ms-java.jar"),
    "spring-jvm-c1":   dict(**spring_cmd(["-Devent-hub.grpc.max-concurrency=1"], jar=str(WORK / "live-ms-java.jar")),
                            health=SPRING_HEALTH, poll=SPRING_POLL, fw=r"Started \w+ in ([\d.]+) seconds", art=WORK / "live-ms-java.jar"),
    "spring-aot":      dict(**spring_cmd(["-XX:AOTCache=app.aot"], jar="live-ms-java.jar", cwd=str(WORK / "spring-aot")),
                            health=SPRING_HEALTH, poll=SPRING_POLL, fw=r"Started \w+ in ([\d.]+) seconds", art=WORK / "spring-aot"),
    "spring-aot-c1":   dict(**spring_cmd(["-XX:AOTCache=app.aot", "-Devent-hub.grpc.max-concurrency=1"], jar="live-ms-java.jar", cwd=str(WORK / "spring-aot")),
                            health=SPRING_HEALTH, poll=SPRING_POLL, fw=r"Started \w+ in ([\d.]+) seconds", art=WORK / "spring-aot"),
    "quarkus-jvm":     dict(cmd=[JAVA, *JVM, *QUARKUS_PROPS, "-jar", "quarkus-run.jar"], cwd=str(WORK / "quarkus-jvm"),
                            health=QUARKUS_HEALTH, poll=QUARKUS_POLL, fw=r"started in ([\d.]+)s", art=WORK / "quarkus-jvm"),
    "quarkus-aot":     dict(cmd=[JAVA, *JVM, "-XX:AOTCache=app.aot", "-XX:AOTMode=on", *QUARKUS_PROPS, "-jar", "quarkus-run.jar"],
                            cwd=str(WORK / "quarkus-aot"), health=QUARKUS_HEALTH, poll=QUARKUS_POLL,
                            fw=r"started in ([\d.]+)s", art=WORK / "quarkus-aot"),
    "quarkus-native":  dict(cmd=[str(WORK / "quarkus-native"), "-Xmx512m", *QUARKUS_PROPS], cwd=str(WORK),
                            health=QUARKUS_HEALTH, poll=QUARKUS_POLL, fw=r"started in ([\d.]+)s", art=WORK / "quarkus-native"),
}
# Spring AOT: -XX:AOTMode defaults to "auto"; make a stale cache fail loudly like on Quarkus.
for k in ("spring-aot", "spring-aot-c1"):
    VARIANTS[k]["cmd"].insert(1, "-XX:AOTMode=on")

def art_size(p):
    p = Path(p)
    if p.is_file():
        return p.stat().st_size
    return sum(f.stat().st_size for f in p.rglob("*") if f.is_file())

def proc_kb(pid, key):
    with open(f"/proc/{pid}/status") as f:
        for line in f:
            if line.startswith(key + ":"):
                return int(line.split()[1])
    return -1

def cpu_seconds(pid):
    with open(f"/proc/{pid}/stat") as f:
        parts = f.read().rsplit(")", 1)[1].split()
    return (int(parts[11]) + int(parts[12])) / os.sysconf("SC_CLK_TCK")

http_mod = http.client
def http(method, port, path, headers=None, timeout=60, conn=None):  # noqa: F811
    c = conn or http_mod.HTTPConnection("127.0.0.1", port, timeout=timeout)
    try:
        c.request(method, path, headers=headers or {})
        r = c.getresponse(); body = r.read()
        return r.status, body
    finally:
        if conn is None:
            c.close()

def hub_stats():
    return json.loads(http("GET", HUB_HTTP, "/stats", timeout=5)[1])

def hub_reset():
    http("POST", HUB_HTTP, "/reset", timeout=5)

class Fakes:
    def start(self):
        pre = ["taskset", "-c", FAKE_CPUS]
        self.log_ais = open(WORK / "fake-ais.log", "w"); self.log_hub = open(WORK / "fake-eventhub.log", "w")
        self.ais = subprocess.Popen([*pre, JAVA, str(BENCH / "FakeAis.java"), str(AIS_PORT), str(N_VESSELS), str(WORK / "ais.p12")], env=ENV,
                                    stdout=self.log_ais, stderr=subprocess.STDOUT)
        cp = (BENCH / "fake-eventhub/target/cp.txt").read_text().strip()
        self.hub = subprocess.Popen([*pre, JAVA, "-cp", f"{BENCH}/fake-eventhub/target/classes:{cp}", "bench.FakeEventHub",
                                     str(HUB_GRPC), str(HUB_HTTP)], env=ENV, stdout=self.log_hub, stderr=subprocess.STDOUT)
        deadline = time.time() + 60
        while time.time() < deadline:
            try:
                c = http_mod.HTTPSConnection("127.0.0.1", AIS_PORT, timeout=2, context=ssl._create_unverified_context())
                c.request("GET", "/vessels/bounding-box"); r = c.getresponse(); body = r.read()
                if r.status == 200 and len(json.loads(body)) == N_VESSELS:
                    hub_stats(); return
            except Exception:
                time.sleep(0.2)
        raise RuntimeError("fakes did not start")
    def stop(self):
        for p in (self.ais, self.hub):
            p.terminate()
        for p in (self.ais, self.hub):
            try: p.wait(10)
            except subprocess.TimeoutExpired: p.kill()

CURRENT = []

def start_app(name, logpath):
    v = VARIANTS[name]
    log = open(logpath, "w")
    # refuse to run if something already answers on the app port (stale process would corrupt the numbers)
    try:
        http("GET", APP_PORT, v["health"], timeout=1); raise RuntimeError("something already listens on the app port")
    except (ConnectionRefusedError, OSError):
        pass
    t0 = time.perf_counter()
    p = subprocess.Popen(["taskset", "-c", APP_CPUS, *v["cmd"]], cwd=v["cwd"], env=ENV, stdout=log, stderr=subprocess.STDOUT)
    CURRENT.append(p)
    deadline = t0 + 120
    while time.perf_counter() < deadline:
        if p.poll() is not None:
            raise RuntimeError(f"{name} exited early rc={p.returncode}, see {logpath}")
        try:
            st, _ = http("GET", APP_PORT, v["health"], timeout=1)
            if st == 200:
                return p, (time.perf_counter() - t0) * 1000
        except Exception:
            pass
        time.sleep(0.010)
    p.kill(); raise RuntimeError(f"{name} not ready in 120s")

def stop_app(p):
    if p.poll() is None:
        p.send_signal(signal.SIGTERM)
        try: p.wait(15)
        except subprocess.TimeoutExpired: p.kill(); p.wait()
    time.sleep(1.0)

def framework_ms(name, logpath):
    m = re.search(VARIANTS[name]["fw"], Path(logpath).read_text(errors="replace"))
    if not m: return None
    return float(m.group(1)) * 1000

def pct(xs, q):
    xs = sorted(xs); k = (len(xs) - 1) * q; f = int(k); c = min(f + 1, len(xs) - 1)
    return xs[f] + (xs[c] - xs[f]) * (k - f)

def startup_run(name, rep, outdir):
    logp = outdir / "logs" / f"startup-{name}-{rep}.log"
    p, ready_ms = start_app(name, logp)
    time.sleep(5.0)
    st = hub_stats()
    row = dict(variant=name, rep=rep, ready_ms_external=round(ready_ms, 1), framework_ms=framework_ms(name, logp),
               rss_idle_kb=proc_kb(p.pid, "VmRSS"), hwm_idle_kb=proc_kb(p.pid, "VmHWM"),
               cpu_s_to_idle=round(cpu_seconds(p.pid), 2), startup_events_seen=st["count"])
    stop_app(p); hub_reset()
    return row

def load_run(name, run, outdir, k, warmup):
    logp = outdir / "logs" / f"load-{name}-{run}.log"
    path, hdrs, ok = VARIANTS[name]["poll"]
    p, ready_ms = start_app(name, logp)
    time.sleep(5.0)
    rss_idle = proc_kb(p.pid, "VmRSS"); cpu_idle = cpu_seconds(p.pid)
    startup_events = hub_stats()["count"]
    conn = http_mod.HTTPConnection("127.0.0.1", APP_PORT, timeout=120)
    for _ in range(warmup):
        st, _ = http("POST", APP_PORT, path, hdrs, conn=conn)
        assert st == ok, f"warmup poll status {st}"
    hub_reset()
    lat = []; t_start = time.perf_counter()
    for i in range(k):
        t = time.perf_counter()
        st, _ = http("POST", APP_PORT, path, hdrs, conn=conn)
        lat.append((time.perf_counter() - t) * 1000)
        assert st == ok, f"poll status {st}"
    total = time.perf_counter() - t_start
    time.sleep(0.5)
    hs = hub_stats()
    rss_after = proc_kb(p.pid, "VmRSS"); hwm = proc_kb(p.pid, "VmHWM"); cpu_after = cpu_seconds(p.pid)
    conn.close(); stop_app(p); hub_reset()
    n_expected = N_VESSELS * k
    return dict(variant=name, run=run, polls=k, events_expected=n_expected, events_received=hs["count"],
                total_s=round(total, 3), events_per_s=round(hs["count"] / total, 1),
                hub_span_s=round((hs["last_ns"] - hs["first_ns"]) / 1e9, 3),
                p50_ms=round(pct(lat, .5), 1), p99_ms=round(pct(lat, .99), 1), max_ms=round(max(lat), 1),
                rss_idle_kb=rss_idle, rss_after_load_kb=rss_after, hwm_after_load_kb=hwm,
                cpu_s_load_phase=round(cpu_after - cpu_idle, 2), startup_events_seen=startup_events), lat

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--startup-reps", type=int, default=7)
    ap.add_argument("--load-runs", type=int, default=5)
    ap.add_argument("--polls", type=int, default=20)
    ap.add_argument("--warmup", type=int, default=3)
    ap.add_argument("--variants", default=",".join(k for k, v in VARIANTS.items() if v["art"].exists()))
    ap.add_argument("--out", default=str(BENCH / "results"))
    ap.add_argument("--only", choices=["startup", "load", "both"], default="both")
    a = ap.parse_args()
    names = a.variants.split(",")
    out = Path(a.out); (out / "logs").mkdir(parents=True, exist_ok=True)
    WORK.mkdir(exist_ok=True)
    fakes = Fakes(); fakes.start()
    try:
        if a.only in ("startup", "both"):
            rows = []
            for rep in range(1, a.startup_reps + 1):          # round-robin: drift hits all variants alike
                for n in names:
                    r = startup_run(n, rep, out); rows.append(r); print("startup", r, flush=True)
            write_csv(out / "startup.csv", rows)
        if a.only in ("load", "both"):
            rows, lats = [], []
            for run in range(1, a.load_runs + 1):
                for n in names:
                    r, lat = load_run(n, run, out, a.polls, a.warmup); rows.append(r); print("load", r, flush=True)
                    lats += [dict(variant=n, run=run, poll=i + 1, latency_ms=round(x, 2)) for i, x in enumerate(lat)]
            write_csv(out / "load.csv", rows); write_csv(out / "poll_latency.csv", lats)
        write_csv(out / "artifacts.csv", [dict(variant=n, bytes=art_size(VARIANTS[n]["art"])) for n in names if VARIANTS[n]["art"].exists()])
    finally:
        for p in CURRENT:
            if p.poll() is None: p.kill()
        fakes.stop()

def write_csv(path, rows):
    with open(path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys())); w.writeheader(); w.writerows(rows)

if __name__ == "__main__":
    main()
