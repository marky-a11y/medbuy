@echo off
setlocal enabledelayedexpansion

:: ===========================================================================
:: start_server.bat
:: Media Buying Dashboard - Windows Server Startup Script
:: ===========================================================================
:: This script builds and starts the Media Buying Dashboard Spring Boot
:: application on a Windows Java environment.
::
:: Prerequisites:
::   - Java 8 (JDK 1.8) installed and JAVA_HOME set
::   - Maven 3.8+ installed and MAVEN_HOME set (or mvn on PATH)
::   - Port 8080 available
:: ===========================================================================

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..
set LOG_DIR=%PROJECT_DIR%\logs
set JAR_FILE=%PROJECT_DIR%\target\media-buying-dashboard.jar
set TIMESTAMP=%DATE:~-4%-%DATE:~4,2%-%DATE:~7,2%_%TIME:~0,2%-%TIME:~3,2%-%TIME:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

:: Ensure logs directory exists
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

:: Record start time
echo ===========================================================================
echo Media Buying Dashboard - Server Startup
echo Timestamp: %DATE% %TIME%
echo Project:   %PROJECT_DIR%
echo ===========================================================================
echo.

:: --------------------------------------------------
:: Step 0: Kill any existing process on port 8080
:: --------------------------------------------------
echo [0/4] Checking for stale process on port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    echo [INFO] Found process PID %%a holding port 8080. Killing...
    taskkill /F /PID %%a >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo [OK] Killed process PID %%a.
    ) else (
        echo [WARN] Could not kill process PID %%a. Attempting to continue...
    )
)
echo [OK] Port 8080 is free.
echo.

:: --------------------------------------------------
:: Step 1: MVN Clean
:: --------------------------------------------------
echo [1/4] Cleaning project...
call mvn clean -f "%PROJECT_DIR%\pom.xml" ^
    >> "%LOG_DIR%\build_%TIMESTAMP%.log" 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven clean failed. Check log: %LOG_DIR%\build_%TIMESTAMP%.log
    pause
    exit /b 1
)
echo [OK] Clean completed successfully.
echo.

:: --------------------------------------------------
:: Step 2: MVN Update (resolve dependencies)
:: --------------------------------------------------
echo [2/4] Updating dependencies (force snapshot refresh)...
call mvn dependency:resolve -U -f "%PROJECT_DIR%\pom.xml" ^
    >> "%LOG_DIR%\build_%TIMESTAMP%.log" 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven dependency resolution failed. Check log: %LOG_DIR%\build_%TIMESTAMP%.log
    pause
    exit /b 1
)
echo [OK] Dependencies resolved successfully.
echo.

:: --------------------------------------------------
:: Step 3: MVN Compile/Build (package JAR, skip tests for faster startup)
:: --------------------------------------------------
echo [3/4] Building application (compiling, packaging JAR)...
call mvn package -DskipTests -Dmaven.test.skip=true -f "%PROJECT_DIR%\pom.xml" ^
    >> "%LOG_DIR%\build_%TIMESTAMP%.log" 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed. Check log: %LOG_DIR%\build_%TIMESTAMP%.log
    pause
    exit /b 1
)
echo [OK] Build completed successfully. JAR: %JAR_FILE%
echo.

:: --------------------------------------------------
:: Step 4: Run the Java main class to start the server
:: --------------------------------------------------
echo [4/4] Starting Media Buying Dashboard server...
echo.
echo Application will start on http://localhost:8080/dashboard.xhtml
echo Press Ctrl+C to stop the server.
echo.

:: --spring.profiles.active=dev uses H2 in-memory database (no external services required)
:: Change to "docker" if you have PostgreSQL/Redis/Kafka running in Docker
:: Change to "k8s" for Kubernetes deployment
::
:: --add-opens flags required for Java 17+ compatibility (CGLIB proxying)

java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.util=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     --add-opens java.base/java.text=ALL-UNNAMED ^
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED ^
    -jar "%JAR_FILE%" ^
    --spring.profiles.active=dev ^
    --server.address=0.0.0.0 ^
    --server.port=8080

:: Capture exit code
set EXIT_CODE=%ERRORLEVEL%

echo.
if %EXIT_CODE% equ 0 (
    echo [INFO] Server stopped gracefully.
) else (
    echo [WARN] Server exited with code: %EXIT_CODE%
)

:: Keep window open on error
if %EXIT_CODE% neq 0 (
    echo.
    echo Check logs at: "%LOG_DIR%"
    pause
)

exit /b %EXIT_CODE%
