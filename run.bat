@echo off
call clean.bat
pushd server
javac -cp ".;lib/*" *.java -encoding UTF-8 
cls
java -cp ".;lib/*" BilClubsServer -encoding UTF-8 
popd
