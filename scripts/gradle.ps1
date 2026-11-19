# Text-only Gradle bootstrap for this repository. No machine-wide installation.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleVersion = '8.11.1'
$gradleSha = 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
$toolsDirectory = Join-Path $projectRoot '.tools'
$gradleExecutable = Join-Path $toolsDirectory "gradle-$gradleVersion/bin/gradle.bat"
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'JDK 17 is required. Install Android Studio and set JAVA_HOME to its jbr directory, or install JDK 17.'
}
if (-not (Test-Path -LiteralPath $gradleExecutable)) {
    New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
    $archivePath = Join-Path $toolsDirectory "gradle-$gradleVersion-bin.zip"
    $partialPath = "$archivePath.part"
    try {
        Write-Host "Downloading Gradle $gradleVersion..."
        Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $partialPath
        if ((Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $gradleSha) {
            throw 'Gradle checksum verification failed.'
        }
        Move-Item -LiteralPath $partialPath -Destination $archivePath -Force
        Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsDirectory -Force
    } finally {
        if (Test-Path -LiteralPath $partialPath) { Remove-Item -LiteralPath $partialPath }
    }
}
& $gradleExecutable -p $projectRoot @args
exit $LASTEXITCODE

