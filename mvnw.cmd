@REM Maven Wrapper for Windows
@REM Automatically uses Maven without requiring a local install

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET MAVEN_WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar

@IF NOT EXIST "%MAVEN_WRAPPER_JAR%" (
    echo ERROR: Maven wrapper jar not found at %MAVEN_WRAPPER_JAR%
    EXIT /B 1
)

@IF DEFINED JAVA_HOME (
    SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
    SET JAVA_EXE=java.exe
)

"%JAVA_EXE%" -classpath "%MAVEN_WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
