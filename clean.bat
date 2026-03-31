@echo off
echo [*] Cleaning compiled class files...
del /s /q "server\*.class" >nul 2>&1
del /s /q "tests\*.class" >nul 2>&1

echo [*] Cleaning logs...
if exist "server\log" rmdir /s /q "server\log"
if exist "server\logs" rmdir /s /q "server\logs"
del /s /q "server\*.log" >nul 2>&1

echo [*] Cleaning objectdb lock/recovery files...
if exist "server\db\*.odb$" del /q /f "server\db\*.odb$" >nul 2>&1

echo [*] Workspace successfully cleaned.
