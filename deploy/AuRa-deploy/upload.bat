@echo off
REM Быстрая заливка на VPS (тот же сценарий, что upload.sh).
REM Нужны: Git Bash или WSL с bash, ssh, rsync в PATH; deploy.env рядом со скриптом.
cd /d "%~dp0"
where bash >nul 2>nul
if errorlevel 1 (
  echo Установите Git for Windows ^(bash^) или добавьте bash в PATH.
  pause
  exit /b 1
)
bash upload.sh
if errorlevel 1 pause
