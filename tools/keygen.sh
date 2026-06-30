#!/bin/bash
# SM2 密钥对生成脚本
# 用法: ./keygen.sh [数量，默认1]
# 前置: 先执行 mvn clean install -DskipTests -pl core

N=${1:-1}
cd "$(dirname "$0")/../sm2-sdk"
mvn -q exec:java -pl core \
  -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen" \
  -Dexec.args="$N"
