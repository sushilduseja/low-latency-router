@echo off
echo Building Low Latency Trading Router Demo...

if exist gradle\wrapper\gradle-wrapper.jar (
  call gradlew -q clean fatJar
  if errorlevel 1 exit /b 1
) else (
  gradle -q clean fatJar
  if errorlevel 1 (
    echo Gradle wrapper not found and Gradle is not installed.
    echo Please install Gradle or restore gradle\wrapper\gradle-wrapper.jar.
    exit /b 1
  )
)

echo Build complete!
echo.
echo To run in standard mode:
echo   java -jar build\libs\low-latency-router-1.0-SNAPSHOT-all.jar standard
echo.
echo To run in zeroGC mode:
echo   java -XX:+UseZGC -XX:+AlwaysPreTouch -XX:+DisableExplicitGC ^
echo        -jar build\libs\low-latency-router-1.0-SNAPSHOT-all.jar zerogc
