@echo off
REM SM2 密钥对生成工具
REM ============================================
REM 用法:
REM   开发期: keygen.bat [数量]
REM   分发后: java -cp sm2-sdk-core-1.0.0.jar;bcprov-jdk18on-1.84.jar com.sm2sdk.core.util.Sm2KeyGen [数量]
REM ============================================

set N=%1
if "%N%"=="" set N=1

cd /d "%~dp0..\sm2-sdk"
echo [生成 %N% 对 SM2 密钥...]
echo.

mvn -q exec:java -pl core ^
  -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen" ^
  -Dexec.args="%N%" 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [提示] 首次使用需先编译: mvn clean install -DskipTests
)
