$adb = "C:\Users\David\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$serial = "emulator-5554"
$artDir = "artifacts"

function Cap([string]$Name) {
    & $adb -s $serial shell screencap -p /sdcard/cap.png
    & $adb -s $serial pull /sdcard/cap.png (Join-Path $artDir "$Name.png")
    Write-Host "Captured $Name"
}

# 1. Inicio
& $adb -s $serial shell input tap 172 290
Start-Sleep -Milliseconds 900
Cap "01_inicio"

# 2. TV En Vivo
& $adb -s $serial shell input tap 172 382
Start-Sleep -Milliseconds 900
Cap "02_tv_en_vivo"

# 3. Películas
& $adb -s $serial shell input tap 172 474
Start-Sleep -Milliseconds 900
Cap "03_peliculas"

# 4. Series
& $adb -s $serial shell input tap 172 566
Start-Sleep -Milliseconds 900
Cap "04_series"

# 5. Buscar
& $adb -s $serial shell input tap 172 658
Start-Sleep -Milliseconds 900
Cap "05_buscar"

# 6. Ajustes
& $adb -s $serial shell input tap 172 750
Start-Sleep -Milliseconds 900
Cap "06_ajustes"

# 7. Añadir lista (Mis listas in sidebar)
& $adb -s $serial shell input tap 172 1002
Start-Sleep -Milliseconds 1000
Cap "07_anadir_lista"
& $adb -s $serial shell input keyevent 4
Start-Sleep -Milliseconds 800

# 8. Comprobacion de actualizacion
& $adb -s $serial shell input tap 172 750
Start-Sleep -Milliseconds 900
# Tap row 4 "Comprobar actualizaciones"
& $adb -s $serial shell input tap 1128 642
Start-Sleep -Milliseconds 1400
Cap "08_comprobacion_actualizacion"
& $adb -s $serial shell input keyevent 4
Start-Sleep -Milliseconds 800

# 9. Reproductor (from Inicio, tap Big Buck Bunny)
& $adb -s $serial shell input tap 172 290
Start-Sleep -Milliseconds 1000
& $adb -s $serial shell input tap 567 937
Start-Sleep -Milliseconds 3500
Cap "09_reproductor"
& $adb -s $serial shell input keyevent 4
Start-Sleep -Milliseconds 800

Write-Host "All 9 screenshots captured!"
