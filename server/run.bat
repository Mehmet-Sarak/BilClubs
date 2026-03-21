cmd /c clean
javac -cp ".;lib/*" *.java -encoding UTF-8 
cls
java -cp ".;lib/*" BilClubsServer -encoding UTF-8 