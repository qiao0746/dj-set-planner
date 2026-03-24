@echo off
setlocal
mvn -q -DskipTests compile exec:java -Dexec.args="%*"
if errorlevel 1 exit /b %errorlevel%

