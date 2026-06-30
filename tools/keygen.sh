#!/bin/bash
# SM2 密钥对生成工具
# ============================================
# 用法:
#   开发期: ./keygen.sh [数量]
#   分发后: java -cp sm2-sdk-core-1.0.0.jar:bcprov-jdk18on-1.84.jar com.sm2sdk.core.util.Sm2KeyGen [数量]
# ============================================

N=${1:-1}
cd "$(dirname "$0")/../sm2-sdk"

if ! mvn -q exec:java -pl core \
  -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen" \
  -Dexec.args="$N" 2>/dev/null; then
  echo ""
  echo "[提示] 首次使用需先编译: mvn clean install -DskipTests"
fi
