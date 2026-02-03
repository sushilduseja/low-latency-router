#!/bin/bash

# Simple script for running the trading router via Gradle

MODE=${1:-both}

echo "Building project..."

if [ -x "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
  ./gradlew -q clean fatJar || exit 1
elif command -v gradle >/dev/null 2>&1; then
  gradle -q clean fatJar || exit 1
else
  echo "Gradle wrapper not found and Gradle is not installed."
  echo "Please install Gradle or restore the Gradle wrapper (gradle/wrapper/gradle-wrapper.jar)."
  exit 1
fi

echo "Running in $MODE mode..."

JAR_PATH="build/libs/low-latency-router-1.0-SNAPSHOT-all.jar"

if [ -z "$MODE" ] || [ "$MODE" == "both" ]; then
  echo "Running sequential benchmark: standard -> zerogc"
  java -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
       -jar "$JAR_PATH" standard || exit 1
  java -XX:+UseZGC -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
       -jar "$JAR_PATH" zerogc || exit 1
  exit 0
fi

if [ "$MODE" == "standard" ]; then
  echo "Using standard allocation mode with default GC settings"
  java -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
       -jar "$JAR_PATH" standard
elif [ "$MODE" == "zerogc" ]; then
  echo "Using ZeroGC mode with optimized settings for low latency"
  java -XX:+UseZGC -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
       -jar "$JAR_PATH" zerogc
else
  echo "Invalid mode: $MODE. Use 'standard', 'zerogc', or 'both'"
  exit 1
fi
