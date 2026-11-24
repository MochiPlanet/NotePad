$ErrorActionPreference = 'Stop'

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$project = 'D:\AndroidWorkSpace\NotePad'
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
$avd = 'NotePadAPI23'

if (-not (Test-Path $emulator)) {
    Write-Host "Emulator not found: $emulator"
    Read-Host 'Press Enter to close'
    exit 1
}

$accelText = (& $emulator -accel-check 2>&1 | Out-String)
if ($accelText -match 'not installed|requires hardware acceleration|not supported') {
    Write-Host 'Hardware acceleration is not available:'
    Write-Host $accelText
    Write-Host ''
    Write-Host 'Please check the AEHD driver with: sc query aehd'
    Read-Host 'Press Enter to close'
    exit 1
}

Write-Host 'Starting the NotePad Android emulator...'
$alreadyRunning = (& $adb devices) -match 'emulator-\d+\s+device'
if (-not $alreadyRunning) {
    Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', $avd,
        '-gpu', 'swiftshader_indirect',
        '-no-snapshot',
        '-no-boot-anim',
        '-netdelay', 'none',
        '-netspeed', 'full'
    )
} else {
    Write-Host 'An emulator is already running.'
}

Write-Host 'Waiting for the emulator to connect...'
$connected = $false
for ($i = 0; $i -lt 60; $i++) {
    $devices = & $adb devices
    if ($devices -match 'emulator-\d+\s+device') {
        $connected = $true
        break
    }
    Start-Sleep -Seconds 3
}

if (-not $connected) {
    Write-Host 'The emulator did not start. Please check the emulator window for errors.'
    Read-Host 'Press Enter to close'
    exit 1
}

Write-Host 'Waiting for Android to boot (the first boot can take a minute)...'
$booted = $false
for ($i = 0; $i -lt 120; $i++) {
    $value = & $adb shell getprop sys.boot_completed 2>$null
    $valueText = ($value -join ' ').Trim()
    if ($valueText -eq '1') {
        $booted = $true
        break
    }
    Start-Sleep -Seconds 3
}

if (-not $booted) {
    Write-Host 'The emulator did not finish booting.'
    Read-Host 'Press Enter to close'
    exit 1
}

Write-Host 'Android is ready. Installing the app...'
if (-not (Test-Path $apk)) {
    Write-Host 'APK not found, building the project...'
    & (Join-Path $project 'gradlew.bat') -p $project :app:assembleDebug
}

$install = Start-Process -FilePath $adb -ArgumentList @('install', '-r', $apk) -Wait -PassThru -NoNewWindow
if ($install.ExitCode -ne 0) {
    Write-Host "adb install failed with exit code $($install.ExitCode)"
    Read-Host 'Press Enter to close'
    exit 1
}

$launch = Start-Process -FilePath $adb -ArgumentList @('shell', 'am', 'start', '-n', 'com.example.android.notepad/.NotesList') -Wait -PassThru -NoNewWindow
if ($launch.ExitCode -ne 0) {
    Write-Host "Could not start the app (exit code $($launch.ExitCode))"
    Read-Host 'Press Enter to close'
    exit 1
}

Write-Host ''
Write-Host 'Done! The NotePad app is now open in the emulator.'
Write-Host 'In Android Studio you can select "emulator-5554" and press Run.'
Start-Sleep -Seconds 6
