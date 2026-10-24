param([switch]$SkipBuild,[string]$JavaHome='C:/Program Files/Java/jdk-17',[string]$Maven='C:/apache-maven-3.8.8/bin/mvn.cmd',[string]$LibDirectory=(Join-Path $PSScriptRoot '../lib'))
$ErrorActionPreference='Stop'
if(-not $SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') -JavaHome $JavaHome -Maven $Maven -LibDirectory $LibDirectory }
$modInfo=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'mod_info.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$stage=Join-Path $PSScriptRoot ('target/package-'+[guid]::NewGuid().ToString('N'))
$modRoot=Join-Path $stage $modInfo.name
New-Item -ItemType Directory -Path (Join-Path $modRoot 'jars') -Force | Out-Null
foreach($name in @('mod_info.json','data','graphics','examples','README.md','接入与兼容说明.md','VALIDATION.md')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $modRoot -Recurse -Force
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'jars/FilmShield.jar') -Destination (Join-Path $modRoot 'jars/FilmShield.jar') -Force
$releaseDir=Join-Path $PSScriptRoot 'releases'
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
$zip=Join-Path $releaseDir ($modInfo.name+'-'+$modInfo.version+'.zip')
Compress-Archive -LiteralPath $modRoot -DestinationPath $zip -Force
Write-Output $zip
