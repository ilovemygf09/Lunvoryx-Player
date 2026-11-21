$adb = "C:\Users\David\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "--- CAPTURING 08: COMPROBACION ACTUALIZACION ---"
# Return to home
& $adb shell am start -n tv.lunvoryx.player/.MainActivity
Start-Sleep -Milliseconds 1200

# Tap Ajustes in the sidebar (x=172, y=600 or let's use keyevents)
# Sidebar items: Inicio(y~230), TV En Vivo(y~300), Peliculas(y~380), Series(y~460), Buscar(y~530), Ajustes(y~600)
& $adb shell input tap 172 600
Start-Sleep -Milliseconds 1000

# Now in Ajustes, row 4 "Comprobar actualizaciones" is around y=360-400, or let's find it with uiautomator
& $adb shell uiautomator dump /sdcard/window_dump.xml
& $adb pull /sdcard/window_dump.xml ./dump_ajustes.xml
