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
; allow install without admin (per-user) if the user prefers
PrivilegesRequiredOverridesAllowed=dialog

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
; everything assembled into the ZG\ folder by the build (ZG.exe + adb/fastboot/heimdall/scrcpy/libusb/zadig)
Source: "ZG\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{group}\ZG"; Filename: "{app}\{#MyAppExe}"
Name: "{group}\Install USB driver (Zadig)"; Filename: "{app}\zadig-2.9.exe"; Check: FileExists(ExpandConstant('{app}\zadig-2.9.exe'))
Name: "{autodesktop}\ZG"; Filename: "{app}\{#MyAppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExe}"; Description: "Launch ZG now"; Flags: nowait postinstall skipifsilent
