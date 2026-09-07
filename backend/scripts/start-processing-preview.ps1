param(
    [Parameter(Mandatory = $true)][string]$FrontendPath,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Pass -JavaHome with a JDK 21+ path or set JAVA_HOME.' }
$env:JAVA_HOME = $JavaHome
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'Java executable not found.' }
$frontendRoot = (Resolve-Path -LiteralPath $FrontendPath).Path
$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    & ./mvnw.cmd '-DskipTests' test-compile dependency:build-classpath '-Dmdep.outputFile=target/preview-classpath.txt' '-Dmdep.includeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Preview compilation failed.' }
    $dependencyClasspath = [System.IO.File]::ReadAllText((Join-Path $backendRoot 'target/preview-classpath.txt')).Trim()
    $previewClasspath = (Join-Path $backendRoot 'target/test-classes') + ';' + (Join-Path $backendRoot 'target/classes') + ';' + $dependencyClasspath
    & $javaExecutable -cp $previewClasspath com.ssafy.upcycle.processing.ProcessingPreview $frontendRoot
    if ($LASTEXITCODE -ne 0) { throw 'Preview server stopped with an error.' }
} finally {
    Pop-Location
}
