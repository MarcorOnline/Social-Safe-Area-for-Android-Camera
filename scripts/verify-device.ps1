# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Marco Rinaldi (MarcorOnline)

param(
    [string]$Adb = 'adb',
    [Parameter(Mandatory = $true)]
    [string]$Serial
)
$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    $output = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return $output
}

function Assert-Overlay([bool]$Expected, [string]$Step) {
    # Allow the accessibility event and Android service lifecycle to finish.
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        $services = Invoke-Adb shell dumpsys activity services it.pistacchissimo.socialoverlay
        $actual = [bool]($services -match 'ServiceRecord.*OverlayService')
        if ($actual -eq $Expected) {
            Write-Output "PASS: $Step (overlay=$actual)"
            return
        }
        Start-Sleep -Milliseconds 200
    }
    throw "FAIL: $Step (expected overlay=$Expected, actual=$actual)"
}

Invoke-Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Invoke-Adb shell am start -n it.pistacchissimo.socialoverlay/.MainActivity | Out-Null
Start-Sleep -Milliseconds 400
$enabled = (Invoke-Adb shell settings get secure enabled_accessibility_services) -join ''
if ($enabled -notmatch 'it\.pistacchissimo\.socialoverlay\.AppWatchAccessibilityService') {
    throw 'Enable Rilevamento app Safe Area in Android Accessibility settings before running this check.'
}
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    $accessibility = (Invoke-Adb shell dumpsys accessibility) -join "`n"
    $bound = [regex]::Match($accessibility, '(?s)Bound services:\{(.*?)\n\s*Enabled services:').Value
    if ($bound -match 'AppWatchAccessibilityService') { break }
    Start-Sleep -Milliseconds 200
}
if ($bound -notmatch 'AppWatchAccessibilityService') {
    throw 'Accessibility is not bound. After instrumentation, reinstall the app APK to reconnect the already-enabled service, then retry.'
}
$prefs = Invoke-Adb shell run-as it.pistacchissimo.socialoverlay cat shared_prefs/social_overlay.xml
if (($prefs -join '') -notmatch 'name="auto_package">com.sec.android.app.camera<') {
    throw 'Select Camera in the app before running this device check.'
}
$originalPrefs = Invoke-Adb shell run-as it.pistacchissimo.socialoverlay cat shared_prefs/social_overlay.xml
$maskBefore = [regex]::Match(($originalPrefs -join ''), 'name="guide_visible" value="(true|false)"').Groups[1].Value
Invoke-Adb shell am start -a android.media.action.VIDEO_CAMERA | Out-Null
Assert-Overlay $true 'Camera opens quick button'
Invoke-Adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
Start-Sleep -Milliseconds 400
Assert-Overlay $false 'Leaving Camera removes quick button and mask'
Invoke-Adb shell am start -a android.media.action.VIDEO_CAMERA | Out-Null
Assert-Overlay $true 'Reopening Camera restores quick button'
Invoke-Adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
Start-Sleep -Milliseconds 400
Assert-Overlay $false 'Leaving Camera again removes overlay'
$finalPrefs = Invoke-Adb shell run-as it.pistacchissimo.socialoverlay cat shared_prefs/social_overlay.xml
$maskAfter = [regex]::Match(($finalPrefs -join ''), 'name="guide_visible" value="(true|false)"').Groups[1].Value
if ($maskBefore -ne $maskAfter) { throw 'FAIL: app transitions changed mask on/off preference' }
Write-Output "PASS: mask preference preserved ($maskAfter)"
