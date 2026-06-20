@rem
@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

"C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
