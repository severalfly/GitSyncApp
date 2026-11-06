#!/bin/sh
# 一键编译 Git 同步（Windows Git Bash）
# 直接调用 Gradle 的 Java 启动类，避开 gradle 包装脚本在 Git Bash 下的路径/挂起问题
set -e
cd "$(dirname "$0")"
export JAVA_HOME="D:/leon/SimpleTextEditor/toolchain/jdk17"
export ANDROID_HOME="D:/leon/SimpleTextEditor/toolchain/android-sdk"
GRADLE_HOME="D:/leon/SimpleTextEditor/toolchain/gradle-8.13"

"$JAVA_HOME/bin/java" -Dorg.gradle.appname=gradle \
  -classpath "$GRADLE_HOME/lib/gradle-launcher-8.13.jar" \
  org.gradle.launcher.GradleMain \
  assembleRelease assembleDebug --no-daemon --console=plain "$@"

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Git同步.apk
cp app/build/outputs/apk/debug/app-debug.apk dist/Git同步-debug.apk
echo "BUILD OK -> dist/Git同步.apk"
