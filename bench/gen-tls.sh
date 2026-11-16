#!/usr/bin/env bash
# Self-signed cert for the fake AIS (SAN: www.aisfriends.com, localhost, 127.0.0.1) + a truststore with just that cert,
# and a hosts file for -Djdk.net.hosts.file (maps the hard-coded external hosts of the Spring app to 127.0.0.1).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
mkdir -p "$WORK_DIR"; cd "$WORK_DIR"
rm -f ais.p12 ais.crt ais-trust.p12
keytool -genkeypair -alias ais -keyalg RSA -keysize 2048 -validity 30 -storetype PKCS12 -keystore ais.p12 -storepass changeit \
  -dname "CN=www.aisfriends.com" -ext "SAN=dns:www.aisfriends.com,dns:localhost,ip:127.0.0.1" 2>/dev/null
keytool -exportcert -alias ais -keystore ais.p12 -storepass changeit -rfc -file ais.crt 2>/dev/null
keytool -importcert -noprompt -alias ais -file ais.crt -storetype PKCS12 -keystore ais-trust.p12 -storepass changeit 2>/dev/null
printf '127.0.0.1 localhost\n127.0.0.1 www.aisfriends.com\n127.0.0.1 www.cgesp.org\n127.0.0.1 api.olhovivo.sptrans.com.br\n' > hosts
ls -la ais.p12 ais-trust.p12 hosts
