@echo off
chcp 65001 >nul
set JAVA_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -XX:+UseZGC
java %JAVA_OPTS% -jar target\ai-agents-1.0.0.jar %*
