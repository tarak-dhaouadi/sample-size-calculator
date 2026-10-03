@echo off
rem Builds dist\TarakDhaouadiSampleSize.jar (needs a JDK 8 or newer on the PATH).
rem   build.bat          compile and package
rem   build.bat test     compile, package, then run the test suites
rem Optional: set VERSION=1.2.0 before calling to change the version stored in the JAR.
setlocal
cd /d "%~dp0"
if "%VERSION%"=="" set VERSION=1.1.0
set PKG=src\main\java\io\github\tarakdhaouadi\samplesize
set TPKG=src\test\java\io\github\tarakdhaouadi\samplesize
set MAIN=io.github.tarakdhaouadi.samplesize

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes
mkdir build\test-classes
mkdir dist

echo Compiling...
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -d build\classes %PKG%\*.java
if errorlevel 1 exit /b 1
xcopy /e /i /q /y src\main\resources build\classes >nul

(
  echo Main-Class: %MAIN%.SampleSizeApp
  echo Implementation-Title: Tarak Dhaouadi for sample size
  echo Implementation-Version: %VERSION%
)> build\MANIFEST.MF
jar cfm dist\TarakDhaouadiSampleSize.jar build\MANIFEST.MF -C build\classes .
if errorlevel 1 exit /b 1
echo Built dist\TarakDhaouadiSampleSize.jar (version %VERSION%)

if /i not "%~1"=="test" exit /b 0

echo Compiling tests...
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -cp build\classes -d build\test-classes %TPKG%\*.java
if errorlevel 1 exit /b 1
echo Running CalcTest...
java -Djava.awt.headless=true -cp "build\classes;build\test-classes" %MAIN%.CalcTest
if errorlevel 1 exit /b 1
echo Running ModulesSmokeTest...
java -Djava.awt.headless=true -cp "build\classes;build\test-classes" %MAIN%.ModulesSmokeTest
if errorlevel 1 exit /b 1
echo All tests passed.
