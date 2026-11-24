@echo off
REM ===== Local Windows build for ZG =====
REM Needs Python 3.10+ installed and on PATH.
setlocal

echo Installing build dependencies...
python -m pip install --upgrade pyinstaller pyusb || goto :err

echo Building ZG.exe...
pyinstaller zg.spec || goto :err

echo.
echo Done. The app is at:  dist\ZG.exe
echo.
echo Put these next to ZG.exe for the matching features:
echo   adb.exe, fastboot.exe      (Google platform-tools)  - Device/Apps/Fastboot tabs
echo   heimdall.exe               (Samsung download flashing)
echo   libusb-1.0.dll             (EUB emergency recovery)
echo   scrcpy.exe (+ its files)   (screen mirroring)
echo.
echo See README.md for driver setup (Zadig / Samsung USB driver).
goto :eof

:err
echo.
echo Build failed. Check the messages above.
exit /b 1
