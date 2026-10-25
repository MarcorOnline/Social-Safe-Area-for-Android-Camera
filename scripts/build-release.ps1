# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Marco Rinaldi (MarcorOnline)
#requires -Version 7.0

param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$AndroidSdk -or !$env:JAVA_HOME) { throw 'Set ANDROID_HOME and JAVA_HOME first.' }
$tools = Join-Path $AndroidSdk 'build-tools/35.0.0'
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
$private = Join-Path $root '.signing'
$keystore = Join-Path $private 'release.p12'
$credentialsPath = Join-Path $private 'release-credentials.xml'

Push-Location $root
try {
    & ./gradlew.bat --no-daemon --console=plain :app:assembleRelease :app:lintRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    if ((Test-Path $keystore) -ne (Test-Path $credentialsPath)) {
        throw 'Incomplete signing files. Restore the original key and credentials; do not generate a replacement.'
    }
    if (!(Test-Path $keystore)) {
        New-Item -ItemType Directory -Force -Path $private | Out-Null
        $bytes = [byte[]]::new(32)
        [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
        $credentials = [pscredential]::new('socialsafearea', $secure)
        # DPAPI protects the password for this Windows user; never commit these files.
        $credentials | Export-Clixml -LiteralPath $credentialsPath
    } else {
        $credentials = Import-Clixml -LiteralPath $credentialsPath
    }
    $env:SSA_RELEASE_PASSWORD = $credentials.GetNetworkCredential().Password
    if (!(Test-Path $keystore)) {
        & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias socialsafearea `
            -keyalg RSA -keysize 3072 -validity 10000 -storepass:env SSA_RELEASE_PASSWORD `
            -keypass:env SSA_RELEASE_PASSWORD -dname 'CN=Social Safe Area, O=Marco Rinaldi, C=IT'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed.' }
    }
    $output = Join-Path $root 'release-artifacts'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $aligned = Join-Path $output 'aligned.apk'
    $metadata = Get-Content -LiteralPath 'app/build/outputs/apk/release/output-metadata.json' -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    $signed = Join-Path $output "Social-Safe-Area-$version.apk"
    & "$tools/zipalign.exe" -f -p 4 'app/build/outputs/apk/release/app-release-unsigned.apk' $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    & "$tools/apksigner.bat" sign --ks $keystore --ks-key-alias socialsafearea `
        --ks-pass env:SSA_RELEASE_PASSWORD --key-pass env:SSA_RELEASE_PASSWORD --out $signed $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & "$tools/apksigner.bat" verify --verbose --print-certs $signed
    if ($LASTEXITCODE -ne 0) { throw 'APK verification failed.' }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $signed).Hash.ToLowerInvariant()
    "$hash  $(Split-Path $signed -Leaf)" | Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "SHA256: $hash"
    Write-Output "Signed APK: $signed"
} finally {
    Remove-Item Env:SSA_RELEASE_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
