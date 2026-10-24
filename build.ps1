param([string]$JavaHome='C:/Program Files/Java/jdk-17',[string]$Maven='C:/apache-maven-3.8.8/bin/mvn.cmd',[string]$LibDirectory=(Join-Path $PSScriptRoot '../lib'),[switch]$SkipContractTests)
$ErrorActionPreference='Stop'
$env:JAVA_HOME=$JavaHome
$LibDirectory=(Resolve-Path -LiteralPath $LibDirectory).Path
& $Maven -q -f (Join-Path $PSScriptRoot 'pom.xml') "-Dstarsector.lib=$LibDirectory" clean package
if($LASTEXITCODE -ne 0) { throw 'Independent library build failed' }
$classpath=@((Join-Path $PSScriptRoot 'target/classes'))
foreach($name in @('starfarer.api.jar','lwjgl.jar','lwjgl_util.jar','LazyLib.jar','log4j-1.2.9.jar','json.jar','Graphics.jar')) { $classpath+=Join-Path $LibDirectory $name }
$classpath=$classpath -join ';'
if(-not $SkipContractTests) {
    $testOut=Join-Path $PSScriptRoot 'target/contract-tests'
    New-Item -ItemType Directory -Force -Path $testOut | Out-Null
    & (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp $classpath -d $testOut (Join-Path $PSScriptRoot 'tests/FilmShieldContractTest.java')
    if($LASTEXITCODE -ne 0) { throw 'Contract test compilation failed' }
    & (Join-Path $JavaHome 'bin/java.exe') -cp ($testOut+';'+$classpath) FilmShieldContractTest
    if($LASTEXITCODE -ne 0) { throw 'Contract tests failed' }
}
$jarDir=Join-Path $PSScriptRoot 'jars'
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target/moci-filmshield-1.0.0.jar') -Destination (Join-Path $jarDir 'FilmShield.jar') -Force
Write-Output 'Independent FilmShield.jar updated in the workspace; no game files were changed.'
