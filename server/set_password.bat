@echo off
title Set SMTP Credentials

set /p EMAIL="Enter email: "
set /p PASSWORD="Enter SMTP password: "

setx SMTP_EMAIL "%EMAIL%"
setx SMTP_PASSWORD "%PASSWORD%"

echo.
echo Credentials have been successfully saved to your environment variables!
echo (Note: You may need to restart your command prompt or applications to see these changes.)
echo.
pause