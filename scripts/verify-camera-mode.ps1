# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

# Real-device check for the initial Samsung profile. Labels default to Italian.
param(
    [string]$Adb = 'adb',
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$PhotoLabel = 'FOTOGRAFIA',
    [string]$VideoLabel = 'VIDEO'
)
$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    $output = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw 'ADB command failed' }
    return $output
}

function Read-CameraControls {
    Invoke-Adb shell rm -f /data/local/tmp/safe-area-mode-test.xml | Out-Null
    Invoke-Adb shell uiautomator dump /data/local/tmp/safe-area-mode-test.xml | Out-Null
    $dump = (Invoke-Adb shell cat /data/local/tmp/safe-area-mode-test.xml) -join ''
    if (!$dump.Contains('<?xml')) { throw 'No fresh camera hierarchy returned' }
    [xml]$tree = $dump.Substring($dump.IndexOf('<?xml'))
    return $tree
}

function Select-Mode([string]$Label) {
    $tree = Read-CameraControls
    $node = $tree.SelectNodes('//node') | Where-Object {
        $_.'resource-id' -eq 'com.sec.android.app.camera:id/shooting_mode_item_button_full' -and $_.text -eq $Label
    } | Select-Object -First 1
    if (!$node -or $node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw 'Expected Samsung mode selector or configured label is unavailable'
    }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Invoke-Adb shell input tap $x $y | Out-Null
}

function Assert-Mode([string]$Mode, [bool]$Visible) {
    $expected = $Visible.ToString().ToLowerInvariant()
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        $watcher = (Invoke-Adb shell dumpsys activity service it.pistacchissimo.socialoverlay/.AppWatchAccessibilityService) -join ''
        $overlay = (Invoke-Adb shell dumpsys activity service it.pistacchissimo.socialoverlay/.OverlayService) -join ''
        if ($watcher -match "mode=$Mode\b" -and $overlay -match "guideVisible=$expected manualEnabled=true") {
            Write-Output "PASS: $Mode (guide=$expected, manual ON preserved)"
            return
        }
        Start-Sleep -Milliseconds 200
    }
    throw "FAIL: $Mode did not produce guide=$expected with manual ON preserved"
}

$prefs = (Invoke-Adb shell run-as it.pistacchissimo.socialoverlay cat shared_prefs/social_overlay.xml) -join ''
if ($prefs -notmatch 'name="experimental_video_only" value="true"' -or
        $prefs -notmatch 'name="auto_package">com.sec.android.app.camera<') {
    throw 'Select Samsung Camera, enable accessibility and the experimental Photo option before this check.'
}
if ($prefs -match 'name="guide_visible" value="false"') { throw 'Turn the guide ON before this check.' }

$originalMode = ''
try {
    Invoke-Adb shell am start -a android.media.action.VIDEO_CAMERA | Out-Null
    Start-Sleep -Milliseconds 800
    $original = (Read-CameraControls).SelectNodes('//node') | Where-Object {
        $_.'resource-id' -eq 'com.sec.android.app.camera:id/shooting_mode_list'
    } | Select-Object -First 1
    $originalMode = ($original.'content-desc' -split ',')[0]
    if ($originalMode -notin @($VideoLabel, $PhotoLabel)) {
        throw 'Start in Video or Photo with the Samsung camera in portrait; supply labels for its language.'
    }
    Select-Mode $VideoLabel
    Assert-Mode 'VIDEO' $true
    Select-Mode $PhotoLabel
    Assert-Mode 'PHOTO' $false
    Select-Mode $VideoLabel
    Assert-Mode 'VIDEO' $true
} finally {
    try {
        if ($originalMode -eq $PhotoLabel) { Select-Mode $PhotoLabel }
    } finally {
        Invoke-Adb shell rm -f /data/local/tmp/safe-area-mode-test.xml | Out-Null
        Invoke-Adb shell input keyevent KEYCODE_HOME | Out-Null
    }
}
