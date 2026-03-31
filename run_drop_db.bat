@echo off
call clean.bat
if exist "server\db" rmdir /s /q "server\db"
pushd server
javac -cp ".;lib/*" *.java -encoding UTF-8 
cls
java -cp ".;lib/*" BilClubsServer -encoding UTF-8 
popd
