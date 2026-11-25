#!/bin/sh
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$APP_HOME/gradle/wrapper"
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 8.9 --project-dir "$APP_HOME"
  elif command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$WRAPPER_JAR" "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
  else
    echo "Error: gradle-wrapper.jar not found and neither gradle, curl, nor wget is available." >&2
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=$(command -v java || true)
fi

if [ -z "$JAVACMD" ] || [ ! -x "$JAVACMD" ]; then
  echo "Error: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  exit 1
fi

exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
