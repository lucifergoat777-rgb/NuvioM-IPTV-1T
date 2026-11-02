# Builds the signed phone/tablet release APKs, and publishes them as a GitHub release when you ask.
#
#   .\scripts\release-mobile.ps1 -Version 0.6.6-beta1                 build only; APKs go to build\releases\0.6.6-beta1
#   .\scripts\release-mobile.ps1 -Version 0.6.6-beta2 -Build 2        a later build (the build number must go up each time)
#   .\scripts\release-mobile.ps1 -Version 0.6.6-beta2 -Build 2 -Beta -Publish -Notes "What changed"
#
# The in-app updater (Settings > About) looks at the releases of $UpdateRepo.
#
# Rules the updater follows:
#   - The release tag is the version, and each version has to be higher than the last.
#   - -Build goes up by one for every release, or Android refuses the update.
#   - Releases are signed with the same key every time. Its path and passwords are in local.properties
#     (NUVIO_RELEASE_STORE_FILE, NUVIO_RELEASE_STORE_PASSWORD, NUVIO_RELEASE_KEY_ALIAS, NUVIO_RELEASE_KEY_PASSWORD).
#     BACK THE KEY UP: without it, installed copies can't be updated.
#   - Each APK's name has to mention its type (arm64-v8a, armeabi-v7a, x86, x86_64) so devices pick the right one.
#   - The updater reads releases without signing in, so the repo (or at least its releases) has to be public for
#     phones to find updates.
param(
    [Parameter(Mandatory = $true)] [string]$Version,
    [int]$Build = 1,
    [string]$UpdateRepo = "zoga2o3-ops/NuvioMPlusIPTV-1Tube",
    [string]$Notes = "",
    [string]$Branch = "main",
    [switch]$Publish,
    [switch]$Beta
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path "$PSScriptRoot\..").Path

if (-not (Test-Path "$repo\local.properties") -or -not (Select-String -Path "$repo\local.properties" -Pattern "NUVIO_RELEASE_STORE_FILE" -Quiet)) {
    throw "No release signing key in local.properties. Releases must be signed with the same key every time."
}
if (-not $env:JAVA_HOME) { throw "Set JAVA_HOME to a JDK 17 or newer (Android Studio's bundled one works)." }
if (-not $env:ANDROID_HOME) { throw "Set ANDROID_HOME to your Android SDK." }

# The version the app reports (and the updater compares) lives in this file.
$xc = "$repo\iosApp\Configuration\Version.xcconfig"
$text = Get-Content $xc -Raw
$text = $text -replace '(?m)^MARKETING_VERSION=.*$', "MARKETING_VERSION=$Version"
$text = $text -replace '(?m)^CURRENT_PROJECT_VERSION=.*$', "CURRENT_PROJECT_VERSION=$Build"
Set-Content -Path $xc -Value $text -NoNewline

Push-Location $repo
try {
    & .\gradlew.bat :androidApp:assembleFullRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

$out = "$repo\build\releases\$Version"
New-Item -ItemType Directory -Force $out | Out-Null
$files = @()
Get-ChildItem "$repo\androidApp\build\outputs\apk\full\release\*.apk" | ForEach-Object {
    $abi = ($_.Name -replace '^androidApp-full-', '' -replace '-release\.apk$', '')
    $dest = "$out\1Tube-Mobile-$Version-$abi.apk"
    Copy-Item $_.FullName $dest -Force
    $files += $dest
    Write-Host "Built $dest"
}

if ($Publish) {
    $flags = @("--repo", $UpdateRepo, "--title", "NuvioM + IPTV + 1Tube $Version", "--target", $Branch, "--notes", $(if ($Notes) { $Notes } else { "NuvioM + IPTV + 1Tube $Version" }))
    if ($Beta) { $flags += "--prerelease" }
    & gh release create $Version @flags @files
    if ($LASTEXITCODE -ne 0) { throw "Publishing failed (exit $LASTEXITCODE)" }
    Write-Host "Published $Version to $UpdateRepo"
} else {
    Write-Host "Not published. Add -Publish to put these on GitHub."
}
