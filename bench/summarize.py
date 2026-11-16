#!/usr/bin/env python3
"""Reads bench/results/*.csv and prints markdown summary tables (median [min-max])."""
import csv, statistics as st, sys
from collections import defaultdict
from pathlib import Path
R = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent / "results"
def rd(n): return list(csv.DictReader(open(R / n)))
def agg(rows, key, col, scale=1.0, fmt="{:.0f}"):
    xs = [float(r[col]) / scale for r in rows if r[col] not in ("", "None")]
    return "n/a" if not xs else (fmt + " [" + fmt + "-" + fmt + "]").format(st.median(xs), min(xs), max(xs))
def group(rows, key="variant"):
    g = defaultdict(list)
    for r in rows: g[r[key]].append(r)
    return g
order = ["spring-jvm", "spring-jvm-c1", "spring-aot", "spring-aot-c1", "quarkus-jvm", "quarkus-aot", "quarkus-native"]
su = group(rd("startup.csv")); ld = group(rd("load.csv")); lat = group(rd("poll_latency.csv"))
art = {r["variant"]: int(r["bytes"]) for r in rd("artifacts.csv")}
def pct(xs, q):
    xs = sorted(xs); k = (len(xs) - 1) * q; f = int(k); c = min(f + 1, len(xs) - 1); return xs[f] + (xs[c] - xs[f]) * (k - f)
print("### Startup (n=%d per variant)\n" % len(next(iter(su.values()))))
print("| variant | ready ext. ms | framework-reported ms | RSS idle MB | CPU s to idle | artifact MB |\n|---|---|---|---|---|---|")
for v in order:
    if v not in su: continue
    s = su[v]
    print(f"| {v} | {agg(s,v,'ready_ms_external')} | {agg(s,v,'framework_ms')} | {agg(s,v,'rss_idle_kb',1024)} | {agg(s,v,'cpu_s_to_idle',1,'{:.1f}')} | {art.get(v,0)/1048576:.0f} |")
print("\n### Load (runs=%d x %s polls x 2000 vessels per variant)\n" % (len(next(iter(ld.values()))), next(iter(ld.values()))[0]["polls"]))
print("| variant | events/s | poll p50 ms (run medians) | poll p50 / p99 ms (pooled) | RSS after load MB | peak RSS (VmHWM) MB | events lost |\n|---|---|---|---|---|---|---|")
for v in order:
    if v not in ld: continue
    l = ld[v]; xs = [float(r["latency_ms"]) for r in lat[v]]
    lost = sum(int(r["events_expected"]) - int(r["events_received"]) for r in l)
    print(f"| {v} | {agg(l,v,'events_per_s')} | {agg(l,v,'p50_ms')} | {pct(xs,.5):.0f} / {pct(xs,.99):.0f} (n={len(xs)}) | {agg(l,v,'rss_after_load_kb',1024)} | {agg(l,v,'hwm_after_load_kb',1024)} | {lost} |")
