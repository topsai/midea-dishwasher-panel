@echo off
call "%~dp0setup_environment.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
start "" "%~dp0.venv\Scripts\pythonw.exe" "%~dp0dishwasher_gui.py"
