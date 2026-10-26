# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

# Prints observations for mode-profile contributions, not executable detection rules.
param(
    [string]$Adb = 'adb',
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z]\w*(\.[A-Za-z]\w*)+$')]
    [string]$CameraPackage
)
$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    $output = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw 'ADB command failed' }
    return $output
}

function Read-Bounds([string]$Value) {
    if ($Value -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Missing camera control bounds' }
    return @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4])
}

try {
    Invoke-Adb shell rm -f /data/local/tmp/safe-area-controls.xml | Out-Null
    Invoke-Adb shell uiautomator dump /data/local/tmp/safe-area-controls.xml | Out-Null
    $raw = (Invoke-Adb shell cat /data/local/tmp/safe-area-controls.xml) -join ''
    $start = $raw.IndexOf('<?xml')
    if ($start -lt 0) { throw 'No accessible camera hierarchy returned' }
    [xml]$tree = $raw.Substring($start)
    $nodes = @($tree.SelectNodes('//node') | Where-Object { $_.package -eq $CameraPackage })
    if (!$nodes.Count) { throw 'Open the specified camera before inspecting its controls' }
    $window = Read-Bounds $nodes[0].bounds
    $width = $window[2] - $window[0]
    $height = $window[3] - $window[1]
    if ($width -le 0 -or $height -le 0) { throw 'Camera window is empty' }
    $controls = @($nodes | Where-Object {
        $_.'resource-id'.StartsWith($CameraPackage + ':id/') -and
            ($_.text -or $_.'content-desc' -or $_.selected -eq 'true')
    } | ForEach-Object {
        $position = Read-Bounds $_.bounds
        [pscustomobject]@{
            view_id = $_.'resource-id'
            text = $_.text
            content_description = $_.'content-desc'
            selected = $_.selected -eq 'true'
            bounds = @(
                [Math]::Round(($position[0] - $window[0]) / $width, 4),
                [Math]::Round(($position[1] - $window[1]) / $height, 4),
                [Math]::Round(($position[2] - $window[0]) / $width, 4),
                [Math]::Round(($position[3] - $window[1]) / $height, 4)
            )
        }
    })
    [pscustomobject]@{
        orientation = $(if ($width -lt $height) { 'portrait' } else { 'landscape' })
        controls = $controls
    } | ConvertTo-Json -Depth 5
} finally {
    Invoke-Adb shell rm -f /data/local/tmp/safe-area-controls.xml | Out-Null
}
