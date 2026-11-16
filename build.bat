@echo off
set TOMCAT=C:\Program Files\Apache Software Foundation\Tomcat 10.1
set APP=%TOMCAT%\webapps\movies5api

if exist out rmdir /s /q out
mkdir out
javac --release 21 -encoding UTF-8 -cp "%TOMCAT%\lib\servlet-api.jar" -d out src\hw5\*.java
if errorlevel 1 (echo COMPILATION FAILED & exit /b 1)

mkdir "%APP%\WEB-INF\classes" 2>nul
xcopy /s /e /y /q out\* "%APP%\WEB-INF\classes\" >nul
copy /y web\WEB-INF\web.xml "%APP%\WEB-INF\web.xml" >nul
echo Test: http://localhost:8080/movies5api/api
