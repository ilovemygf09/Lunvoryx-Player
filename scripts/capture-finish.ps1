$adb = "C:\Users\David\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "--- 1. Tap Comprobar actualizaciones (x=1128, y=642) ---"
& $adb shell input tap 1128 642
Start-Sleep -Milliseconds 1500

Write-Host "--- 2. Capturing 08_comprobacion_actualizacion.png ---"
& $adb shell screencap -p /sdcard/cap08.png
& $adb pull /sdcard/cap08.png artifacts/08_comprobacion_actualizacion.png

Write-Host "--- 3. Dismiss dialog ---"
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 600

Write-Host "--- 4. Go to Inicio (x=172, y=290) ---"
& $adb shell input tap 172 290
Start-Sleep -Milliseconds 1200

Write-Host "--- 5. Tap Big Buck Bunny (x=567, y=937) ---"
& $adb shell input tap 567 937
Start-Sleep -Milliseconds 4000

Write-Host "--- 6. Capturing 09_reproductor.png ---"
& $adb shell screencap -p /sdcard/cap09.png
& $adb pull /sdcard/cap09.png artifacts/09_reproductor.png

Write-Host "--- 7. Exit player ---"
& $adb shell input keyevent 4
Start-Sleep -Milliseconds 500

Write-Host "Done!"
