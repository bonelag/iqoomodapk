param(
    [string]$Sdk = 'D:/Code/REMOTE_DESKTOP/android',
    [string]$JavaHome = 'D:/Code/REMOTE_DESKTOP/android/jdk17'
)
$ErrorActionPreference = 'Stop'
& python "$PSScriptRoot/tools/build.py" --sdk $Sdk --java-home $JavaHome
if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
