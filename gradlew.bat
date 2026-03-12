@ECHO OFF
SET DIR=%~dp0
SET JAR=%DIR%gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%JAR%" (
  ECHO Missing gradle-wrapper.jar. Install Gradle locally and run "gradle wrapper", or add the wrapper jar.
  EXIT /B 1
)

IF "%JAVA_HOME%"=="" (
  java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  "%JAVA_HOME%\bin\java.exe" -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
)

