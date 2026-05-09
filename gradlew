#!/bin/bash
DIRNAME=$(dirname "$0")
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$DIRNAME" && pwd -P) || exit

# 使用系统 gradle 如果存在
if command -v gradle &> /dev/null; then
    exec gradle "$@"
fi

# 检查 JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/opt/android-studio/jbr" ]; then
        export JAVA_HOME="/opt/android-studio/jbr"
    fi
fi

# 检查 Android SDK
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
fi

# 尝试使用 android studio 自带的 gradle
if [ -d "/opt/android-studio/plugins/gradle" ]; then
    GRADLE_PLUGIN_DIR="/opt/android-studio/plugins/gradle"
    for lib in "$GRADLE_PLUGIN_DIR"/lib/*/gradle-*.jar; do
        [ -f "$lib" ] && CLASSPATH="$CLASSPATH:$lib"
    done
    if [ -n "$CLASSPATH" ]; then
        exec java -cp "$CLASSPATH" gradle.wrapper.GradleWrapperMain "$@"
    fi
fi

echo "Error: Gradle not found. Please install Gradle or set JAVA_HOME."
exit 1
