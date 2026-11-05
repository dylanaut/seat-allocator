#!/usr/bin/env sh
# Startet die Seat-Allocator-Demo (macOS, Linux). Voraussetzung: Java 21 oder neuer.
# Optionen als Umgebungsvariablen: PORT (Standard 8080), NO_BROWSER=1 (Browser nicht öffnen), JAVA_HOME.
cd "$(dirname "$0")" || exit 1

JAR="seat-allocator-demo.jar"
PORT="${PORT:-8080}"
URL="http://localhost:${PORT}/"

JAVA=java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
  echo "Java wurde nicht gefunden. Bitte Java 21 oder neuer installieren (z. B. https://adoptium.net)." >&2
  exit 1
fi

VERSION=$("$JAVA" -version 2>&1 | sed -n '1s/.*"\([0-9][0-9]*\)[."-].*/\1/p')
if [ -z "$VERSION" ] || [ "$VERSION" -lt 21 ]; then
  echo "Java 21 oder neuer erforderlich, gefunden: $("$JAVA" -version 2>&1 | head -n 1)" >&2
  exit 1
fi

echo "Starte die Seat-Allocator-Demo auf ${URL} (Beenden mit Strg+C)"

if [ -z "$NO_BROWSER" ]; then
  if command -v open >/dev/null 2>&1; then
    (sleep 6; open "$URL") >/dev/null 2>&1 &
  elif command -v xdg-open >/dev/null 2>&1; then
    (sleep 6; xdg-open "$URL") >/dev/null 2>&1 &
  fi
fi

exec "$JAVA" -Dquarkus.http.port="$PORT" -jar "$JAR" "$@"
