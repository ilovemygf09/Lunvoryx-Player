$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$serial = 'emulator-5554'
$root = Split-Path $PSScriptRoot -Parent
$artDir = Join-Path $root 'artifacts'

function Capture([string]$Name) {
    & $adb -s $serial shell screencap -p /sdcard/cap.png
    & $adb -s $serial pull /sdcard/cap.png (Join-Path $artDir "$Name.png")
    Write-Host "Captured $Name"
}

# 1. Inicio
& $adb -s $serial shell input tap 172 290
Start-Sleep -Milliseconds 800
Capture "01_inicio"

# 2. TV En Vivo
& $adb -s $serial shell input tap 172 382
Start-Sleep -Milliseconds 800
Capture "02_tv_en_vivo"

# 3. Peliculas
& $adb -s $serial shell input tap 172 474
Start-Sleep -Milliseconds 800
Capture "03_peliculas"

# 4. Series
& $adb -s $serial shell input tap 172 566
Start-Sleep -Milliseconds 800
Capture "04_series"

# 5. Buscar
& $adb -s $serial shell input tap 172 658
Start-Sleep -Milliseconds 800
Capture "05_buscar"

# 6. Ajustes
& $adb -s $serial shell input tap 172 750
Start-Sleep -Milliseconds 800
Capture "06_ajustes"

# 7. Añadir lista
& $adb -s $serial shell input tap 172 1002
Start-Sleep -Milliseconds 1000
Capture "07_anadir_lista"
& $adb -s $serial shell input keyevent 4
Start-Sleep -Milliseconds 800

# 8. Comprobacion de actualizacion
& $adb -s $serial shell input tap 172 750
Start-Sleep -Milliseconds 800
& $adb -s $serial shell input tap 600 642
Start-Sleep -Milliseconds 1200
Capture "08_comprobacion_actualizacion"
& $adb -s $serial shell input keyevent 4
Start-Sleep -Milliseconds 800

# 9. Reproductor
& $adb -s $serial shell input tap 172 290
Start-Sleep -Milliseconds 800
& $adb -s $serial shell input tap 300 920
Start-Sleep -Seconds 4
Capture "09_reproductor"
& $adb -s $serial shell input keyevent 4
Start-Sleep -Milliseconds 600

Write-Host "All screenshots successfully captured with Apple-style borders."
