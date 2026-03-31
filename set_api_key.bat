@echo off
title Set Gemini API Key

set /p KEY="Enter API KEY: "

setx GEMINI_API_KEY "%KEY%"

echo.
echo API key has been successfully saved to your environment variables!
echo (Note: You may need to restart your command prompt or applications to see these changes.)
echo.
pause