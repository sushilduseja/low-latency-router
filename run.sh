#!/bin/bash

# Simple script for running the trading router demo via Gradle

if [ $# -ne 1 ]; then
  echo "Usage: $0 [standard|zerogc]"
  exit 1
fi

MODE=$1

# Validate mode
if [ "$MODE" != "standard" ] && [ "$MODE" != "zerogc" ]; then
  echo "Invalid mode: $MODE. Use 'standard' or 'zerogc'"
  exit 1
fi

echo "Building project..."

if [ -x "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
  ./gradlew -q clean fatJar
elif command -v gradle >/dev/null 2>&1; then
  gradle -q clean fatJar
else
  echo "Gradle wrapper not found and Gradle is not installed."
  echo "Please install Gradle or restore the Gradle wrapper (gradle/wrapper/gradle-wrapper.jar)."
  exit 1
fi

echo "Running in $MODE mode..."

JAR_PATH="build/libs/low-latency-router-1.0-SNAPSHOT-all.jar"

if [ "$MODE" == "standard" ]; then
  echo "Using standard allocation mode with default GC settings"
  java -jar "$JAR_PATH" standard
else
  echo "Using ZeroGC mode with optimized settings for low latency"
  java -XX:+UseZGC -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
       -jar "$JAR_PATH" zerogc
fi
