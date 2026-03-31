@echo off
echo [*] Cleaning environment...
call kill_server.bat
if exist "server\db\*" del /q "server\db\*" >nul 2>&1

echo [*] Compiling server...
pushd server
javac -cp ".;lib/*" *.java -encoding UTF-8 

echo [*] Starting server in background...
start "BilClubsServer" /D "%~dp0server" /MIN cmd /c java -cp ".;lib/*" BilClubsServer -encoding UTF-8 ^> server.log 2^>^&1
popd

echo [*] Waiting for server to initialize...
ping 127.0.0.1 -n 4 > nul

echo [*] Compiling tests...
pushd tests
javac -cp ".;..\server\lib\*" *.java -encoding UTF-8

echo [*] Running test suite...
java -cp ".;..\server\lib\*" TestSuite
popd

echo [*] Tests complete. Cleaning up...
call kill_server.bat
if exist "server\db\*" del /q "server\db\*" >nul 2>&1
call clean.bat
echo [*] Done.
