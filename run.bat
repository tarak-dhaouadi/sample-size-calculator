@echo off
rem Starts the application from the JAR built by build.bat (or downloaded from the Releases page).
setlocal
set JAR=%~dp0dist\TarakDhaouadiSampleSize.jar
if not exist "%JAR%" set JAR=%~dp0TarakDhaouadiSampleSize.jar
if not exist "%JAR%" (
  echo TarakDhaouadiSampleSize.jar not found. Run build.bat first, or download it from the Releases page.
  pause
  exit /b 1
)
start "" javaw -jar "%JAR%"
