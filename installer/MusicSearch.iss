[Setup]
AppName=MusicSearch
AppVersion=1.0
DefaultDirName={pf}\MusicSearch
DefaultGroupName=MusicSearch
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Files]
Source: "C:\Projects\MusicSearch\app\build\launch4j\MusicSearch.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Projects\MusicSearch\app\build\custom-runtime\*"; DestDir: "{app}\runtime"; Flags: recursesubdirs createallsubdirs
OutputDir=..\app\build\installer

[Icons]
Name: "{group}\MusicSearch"; Filename: "{app}\MusicSearch.exe"
