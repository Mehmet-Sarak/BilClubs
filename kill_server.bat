@echo off
FOR /F "tokens=5" %%P IN ('netstat -a -n -o ^| findstr :5000') DO (
    IF NOT "%%P"=="0" (
        taskkill /F /PID %%P 2>nul
    )
)
echo Port 5000 cleared.
