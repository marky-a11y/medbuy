#!/bin/bash
# ============================================================================
# start.sh — Media Buying Dashboard launcher for Java 17+ / Railway
#
# Java 17+ restricts reflective access (JPMS module system), which breaks
# CGLIB proxying used by Spring Boot 2.7.x + JoinFaces. The --add-opens flags
# below restore the required access.
#
# Usage:
#   ./start.sh                          # uses target/*.jar
#   ./start.sh path/to/myapp.jar        # explicit JAR path
#
# Railway setup:
#   1. Set the start command in your Railway dashboard or railway.json to:
#      bash start.sh
#   2. Or set the environment variable JAVA_TOOL_OPTIONS to the --add-opens list
#      (see ADD_OPENS below) and keep the default java -jar command.
# ============================================================================

set -euo pipefail

ADD_OPENS="\
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.text=ALL-UNNAMED \
--add-opens java.desktop/java.awt.font=ALL-UNNAMED"

# Find the JAR
if [ $# -ge 1 ]; then
    JAR="$1"
else
    # auto-detect in target/
    JAR=$(ls -t target/media-buying-dashboard*.jar 2>/dev/null | head -1)
    if [ -z "$JAR" ]; then
        echo "ERROR: No JAR found in target/. Build first with: mvn package -DskipTests" >&2
        exit 1
    fi
fi

echo "Starting Media Buying Dashboard with --add-opens (Java 17+ compatibility)..."
echo "  JAR: $JAR"
echo "  JVM: $(java -version 2>&1 | head -1)"

exec java $ADD_OPENS -jar "$JAR"
