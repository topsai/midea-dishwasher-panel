@echo off
call "%~dp0setup_environment.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
"%~dp0.venv\Scripts\python.exe" "%~dp0read_temperature.py"
pause
