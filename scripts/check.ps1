# Portable check entry point (Windows PowerShell 5.1 / PowerShell 7).
param([ValidateSet('unit', 'e2e', 'tools')][string]$Mode = 'unit')
$ErrorActionPreference = 'Stop'
$checkRoot = Split-Path -Parent $PSScriptRoot
$checkWindows = $env:OS -eq 'Windows_NT'

function Find-Tool([string[]]$Names) {
    foreach ($toolName in $Names) {
        $toolCommand = Get-Command $toolName -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($toolCommand) { return $toolCommand.Source }
    }
    throw ('Missing tool: ' + ($Names -join ' / '))
}

try {
    if ($env:JAVA_HOME) {
        $checkJava = Join-Path $env:JAVA_HOME 'bin/java'
        $checkJavac = Join-Path $env:JAVA_HOME 'bin/javac'
        if ($checkWindows) { $checkJava += '.exe'; $checkJavac += '.exe' }
        if (!(Test-Path -LiteralPath $checkJava) -or !(Test-Path -LiteralPath $checkJavac)) { throw 'JAVA_HOME must point to a JDK.' }
    } else {
        $checkJava = Find-Tool @('java')
        $checkJavac = Find-Tool @('javac')
    }
    # Java writes version information to stderr, including on success.
    $ErrorActionPreference = 'Continue'
    $checkJavaVersion = (& $checkJava -version 2>&1 | Out-String)
    $checkJavaExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($checkJavaExit -ne 0 -or $checkJavaVersion -notmatch 'version "(\d+)') { throw 'Cannot read Java version.' }
    if ([int]$Matches[1] -lt 17) { throw 'JDK 17 or newer is required.' }
    $checkNode = Find-Tool @('node')
    $checkNpm = Find-Tool @('npm.cmd', 'npm')
    $checkNodeVersion = [version](& $checkNode -p 'process.versions.node')
    if (!(($checkNodeVersion.Major -eq 22 -and $checkNodeVersion -ge [version]'22.18.0') -or $checkNodeVersion -ge [version]'24.12.0')) { throw 'Use Node 22.18+ (22.x) or Node 24.12+; see frontend/package.json.' }
    $checkMaven = $env:MVN
    if ($checkMaven -and !(Test-Path -LiteralPath $checkMaven)) { throw 'MVN must point to the Maven executable.' }
    if (!$checkMaven) {
        $checkMavenCommand = Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($checkMavenCommand) { $checkMaven = $checkMavenCommand.Source }
    }
    if (!$checkMaven -and $checkWindows) {
        $checkCache = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/wrapper/dists'
        if (Test-Path -LiteralPath $checkCache) {
            $checkCachedMaven = Get-ChildItem -LiteralPath $checkCache -Recurse -Filter mvn.cmd | Sort-Object FullName -Descending | Select-Object -First 1
            if ($checkCachedMaven) { $checkMaven = $checkCachedMaven.FullName }
        }
        if (!$checkMaven) { throw 'Install Maven on PATH, or run backend/mvnw.cmd -v once in an interactive terminal to populate the wrapper cache (needed for Unicode paths).' }
    }
    if (!$checkMaven) { $checkMaven = Join-Path $checkRoot 'backend/mvnw' }
    Write-Host ('Java: ' + $checkJava)
    Write-Host ('Maven: ' + $checkMaven)
    Write-Host ('Node: ' + $checkNodeVersion)
    if ($Mode -eq 'tools') { exit 0 }
    if ($Mode -eq 'unit') {
        Push-Location -LiteralPath (Join-Path $checkRoot 'backend')
        try {
            if (!$checkWindows -and $checkMaven -eq (Join-Path $checkRoot 'backend/mvnw')) { & bash $checkMaven -B -q test }
            else { & $checkMaven -B -q test }
            if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
        } finally { Pop-Location }
    }
    Push-Location -LiteralPath (Join-Path $checkRoot 'frontend')
    try {
        if ($Mode -eq 'e2e') { & $checkNpm run test:e2e } else { & $checkNpm test }
        if ($LASTEXITCODE -ne 0) { throw 'Frontend checks failed.' }
    } finally { Pop-Location }
    Write-Host 'Checks passed.' -ForegroundColor Green
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
