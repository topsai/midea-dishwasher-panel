param([string]$Adb = "adb")
$ErrorActionPreference = "Stop"
$AndroidRoot = Split-Path $PSScriptRoot -Parent
& "$AndroidRoot/gradlew.bat" -p $AndroidRoot :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw "Test APK build failed" }
& $Adb install -r "$AndroidRoot/app/build/outputs/apk/debug/app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "App installation failed" }
& $Adb install -r "$AndroidRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if ($LASTEXITCODE -ne 0) { throw "Test installation failed" }
$result = & $Adb shell am instrument -w -r com.topsai.dishwasher.test/android.test.InstrumentationTestRunner
$result | Write-Output
if (($result -join "\n") -notmatch 'OK \([0-9]+ tests\)') { throw "Device instrumentation failed" }

