#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"
exec java "-classpath" "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "org.gradle.wrapper.GradleWrapperMain" "$@"
