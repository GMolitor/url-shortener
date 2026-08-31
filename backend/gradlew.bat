@echo off
setlocal
set DIR=%~dp0
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" goto run
echo Gradle wrapper JAR is missing. Run the documented bootstrap command or restore gradle-wrapper.jar.
exit /b 1
:run
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
