#!/bin/sh
# Maven Wrapper Script
# Automatically downloads and uses Maven without requiring a local install

set -e

MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  DISTRIBUTION_URL=$(grep -E "^distributionUrl=" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"

# Use JAVA_HOME if set
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
