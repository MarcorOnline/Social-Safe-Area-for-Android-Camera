# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Marco Rinaldi (MarcorOnline)
#requires -Version 7.0

param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$Commit,
    [Parameter(Mandatory = $true)][string]$NotesPath,
    [switch]$Prerelease
)
$ErrorActionPreference = 'Stop'
$repo = 'MarcorOnline/SocialSafeArea'
$root = Split-Path $PSScriptRoot -Parent
$metadata = Get-Content -LiteralPath (Join-Path $root 'app/build/outputs/apk/release/output-metadata.json') -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ($Tag -cne "v$version") { throw 'Release tag must match the built APK version.' }
$apkName = "Social-Safe-Area-$version.apk"
$assets = @($apkName, 'SHA256SUMS.txt')
foreach ($asset in $assets) {
    if (!(Test-Path -LiteralPath (Join-Path $root "release-artifacts/$asset"))) { throw "Missing $asset" }
}
$hash = (Get-FileHash -LiteralPath (Join-Path $root "release-artifacts/$apkName") -Algorithm SHA256).Hash.ToLowerInvariant()
if ((Get-Content -LiteralPath (Join-Path $root 'release-artifacts/SHA256SUMS.txt') -Raw).Trim() -cne "$hash  $apkName") {
    throw 'APK checksum does not match SHA256SUMS.txt.'
}
# Git's configured credential helper supplies authentication; never print its output.
$lines = "protocol=https`nhost=github.com`n`n" | git credential fill
if ($LASTEXITCODE -ne 0) { throw 'GitHub credential lookup failed.' }
$credentials = @{}
foreach ($line in $lines) {
    $pair = $line -split '=', 2
    if ($pair.Length -eq 2) { $credentials[$pair[0]] = $pair[1] }
}
if (!$credentials.password) { throw 'No GitHub credential is configured.' }
$headers = @{
    Authorization = "Bearer $($credentials.password)"
    Accept = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent' = 'Social-Safe-Area-release'
}
$api = "https://api.github.com/repos/$repo/releases"
try {
    $release = $null
    try { $release = Invoke-RestMethod -Uri "$api/tags/$Tag" -Headers $headers }
    catch { if ([int]$_.Exception.Response.StatusCode -ne 404) { throw } }
    if ($release -and !$release.draft) { throw 'Release is already public; published assets will not be replaced.' }
    if (!$release) {
        $body = @{ tag_name = $Tag; target_commitish = $Commit; name = "Social Safe Area $Tag";
            body = (Get-Content -LiteralPath $NotesPath -Raw); draft = $true; prerelease = $Prerelease.IsPresent } | ConvertTo-Json
        $release = Invoke-RestMethod -Method Post -Uri $api -Headers $headers -ContentType 'application/json' -Body $body
    }
    foreach ($asset in $assets) {
        $path = Join-Path $root "release-artifacts/$asset"
        $existing = $release.assets | Where-Object name -eq $asset
        if ($existing) {
            $digest = 'sha256:' + (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($existing.digest -ne $digest) { throw "Existing draft asset differs: $asset" }
            continue
        }
        $upload = ($release.upload_url -replace '\{.*$', '') + '?name=' + [uri]::EscapeDataString($asset)
        $null = Invoke-RestMethod -Method Post -Uri $upload -Headers $headers `
            -ContentType 'application/octet-stream' -InFile $path
    }
    $latest = if ($Prerelease) { 'false' } else { 'true' }
    $result = Invoke-RestMethod -Method Patch -Uri "$api/$($release.id)" -Headers $headers `
        -ContentType 'application/json' -Body (@{ draft = $false; prerelease = $Prerelease.IsPresent; make_latest = $latest } | ConvertTo-Json)
    Write-Output "Published: $($result.html_url)"
} finally {
    $credentials.Clear()
    $headers.Clear()
    $lines = $null
}
