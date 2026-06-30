@echo off
REM SM2 密钥对生成脚本
REM 用法: keygen.bat [数量，默认1]
REM 前置: 先执行 mvn clean install -DskipTests -pl core

set N=%1
if "%N%"=="" set N=1

cd /d "%~dp0..\sm2-sdk"
mvn -q exec:java -pl core -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen" -Dexec.args="%N%"
