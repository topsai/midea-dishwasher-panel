@echo off
if exist "%~dp0.venv\.dependencies-ready" if exist "%~dp0.venv\Scripts\python.exe" exit /b 0
if not exist "%~dp0.venv\Scripts\python.exe" (
  python -m venv "%~dp0.venv"
  if errorlevel 1 exit /b 1
)
"%~dp0.venv\Scripts\python.exe" -m pip install -r "%~dp0requirements.txt"
if errorlevel 1 (
  echo Setup failed. Check your network and run this launcher again.
  exit /b 1
)
type nul > "%~dp0.venv\.dependencies-ready"
