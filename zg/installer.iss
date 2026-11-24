; Inno Setup script for ZG — produces a single ZG-Setup.exe installer.
; Compiled on Windows by the GitHub Actions build (ISCC installer.iss).

#define MyAppName "ZG"
#define MyAppVersion "1.0"
#define MyAppExe "ZG.exe"

[Setup]
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=ZG
DefaultDirName={autopf}\ZG
DefaultGroupName=ZG
DisableProgramGroupPage=yes
OutputDir=Output
OutputBaseFilename=ZG-Setup
SetupIconFile=zg.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64
; driver installation needs elevation
PrivilegesRequired=admin

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"
Name: "drivers"; Description: "Install USB drivers now (Samsung + WinUSB for download/EUB modes)"; GroupDescription: "Drivers:"

[Files]
; everything assembled into the ZG\ folder by the build (ZG.exe + adb/fastboot/heimdall/scrcpy/libusb/zadig + driver helpers)
Source: "ZG\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\ZG"; Filename: "{app}\{#MyAppExe}"
Name: "{group}\Install USB driver (Zadig)"; Filename: "{app}\zadig-2.9.exe"; Check: FileExists(ExpandConstant('{app}\zadig-2.9.exe'))
Name: "{autodesktop}\ZG"; Filename: "{app}\{#MyAppExe}"; Tasks: desktopicon

[Run]
; Samsung USB driver (adb / fastboot / download recognition) — runs only if it was bundled
Filename: "{app}\SAMSUNG_USB_Driver.exe"; Parameters: "/S"; StatusMsg: "Installing Samsung USB driver..."; Flags: waituntilterminated; Tasks: drivers; Check: FileExists(ExpandConstant('{app}\SAMSUNG_USB_Driver.exe'))
; WinUSB for Samsung download mode (Heimdall) — 04E8:685D
Filename: "{app}\wdi-simple.exe"; Parameters: "--vid 0x04E8 --pid 0x685D --type 0 --name ""Samsung Download (WinUSB)"""; StatusMsg: "Installing download-mode driver..."; Flags: runhidden waituntilterminated; Tasks: drivers; Check: FileExists(ExpandConstant('{app}\wdi-simple.exe'))
; WinUSB for Exynos EUB emergency mode — 04E8:1234
Filename: "{app}\wdi-simple.exe"; Parameters: "--vid 0x04E8 --pid 0x1234 --type 0 --name ""Samsung EUB (WinUSB)"""; StatusMsg: "Installing EUB driver..."; Flags: runhidden waituntilterminated; Tasks: drivers; Check: FileExists(ExpandConstant('{app}\wdi-simple.exe'))
Filename: "{app}\{#MyAppExe}"; Description: "Launch ZG now"; Flags: nowait postinstall skipifsilent
