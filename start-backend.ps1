param([string]$JavaHome)

$ErrorActionPreference = 'Stop'
if (-not $JavaHome) {
    $localJdk = Get-ChildItem "$env:LOCALAPPDATA\TranslatorTools" -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $JavaHome = $localJdk.FullName }
    else { $JavaHome = $env:JAVA_HOME }
}
if (-not $JavaHome -or -not (Test-Path "$JavaHome\bin\java.exe")) {
    throw 'Install JDK 25 and set JAVA_HOME, or supply -JavaHome with its directory.'
}
$version = & "$JavaHome\bin\java.exe" -version 2>&1 | Out-String
if ($version -notmatch 'version "25[.\"]') { throw 'This project requires JDK 25. Supply -JavaHome pointing to JDK 25.' }
$env:JAVA_HOME = $JavaHome
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & mvn -f backend/pom.xml package -DskipTests -B
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    & "$JavaHome\bin\java.exe" -jar backend/target/translator-0.0.1-SNAPSHOT.jar
} finally { Pop-Location }