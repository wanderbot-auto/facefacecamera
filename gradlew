#!/bin/sh

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$JAR" ]; then
  echo "Missing gradle-wrapper.jar. Install Gradle locally and run 'gradle wrapper', or add the wrapper jar." >&2
  exit 1
fi

if [ -z "$JAVA_HOME" ] && ! command -v java >/dev/null 2>&1; then
  echo "Java is required to run Gradle." >&2
  exit 1
fi

exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"

