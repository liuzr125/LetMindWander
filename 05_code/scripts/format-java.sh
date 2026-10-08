#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
formatter="${JAVA_FORMAT_JAR:-$HOME/.m2/repository/com/google/googlejavaformat/google-java-format/1.7/google-java-format-1.7-all-deps.jar}"
if [ ! -f "$formatter" ]; then
  echo 'Google Java Format 1.7 jar missing; set JAVA_FORMAT_JAR to an existing jar.' >&2
  exit 1
fi
if [ "${1:-}" = '--check' ]; then
  find backend/src/main/java backend/src/test/java backend/tools -name '*.java' -print0 | xargs -0 java -jar "$formatter" --dry-run --set-exit-if-changed
else
  find backend/src/main/java backend/src/test/java backend/tools -name '*.java' -print0 | xargs -0 java -jar "$formatter" --replace
fi
