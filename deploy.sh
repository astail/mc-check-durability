#!/usr/bin/env bash
#
# DuraAlert の jar をビルドするだけのスクリプト（Mac ネイティブ / JDK25 + Maven）。
# 生成物: target/DuraAlert-1.0.1.jar
#
set -euo pipefail

# JDK 25（Homebrew の openjdk@25）。別の場所の JDK を使う場合は JAVA_HOME を環境変数で上書き可。
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mvn -f "$PROJECT_DIR/pom.xml" -B clean package
