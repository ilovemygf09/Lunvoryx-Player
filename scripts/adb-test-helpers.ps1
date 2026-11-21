param([string]$Serial = 'emulator-5554')
$script:LunvoryxAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$script:LunvoryxSerial = $Serial
$script:LunvoryxRoot = Split-Path $PSScriptRoot -Parent
function Invoke-TvAdb {
    & $script:LunvoryxAdb -s $script:LunvoryxSerial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($args -join ' ')" }
}
function Get-TvUi {
    $ready = $false
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $result = Invoke-TvAdb shell uiautomator dump /sdcard/lunvoryx-ui.xml
        if (($result -join ' ') -match 'dumped to') { $ready = $true; break }
        Start-Sleep -Milliseconds 600
    }
    if (-not $ready) { throw 'Android UI hierarchy unavailable' }
    $target = Join-Path $script:LunvoryxRoot '.local\ui.xml'
    Invoke-TvAdb pull /sdcard/lunvoryx-ui.xml $target | Out-Null
    [xml](Get-Content -LiteralPath $target -Raw)
}
function Show-TvFocus {
    $ui = Get-TvUi
    $ui.SelectNodes('//node[@focused="true"]') | Select-Object text,content-desc,bounds
}
function Click-TvControl([string]$Label) {
    $ui = Get-TvUi
    $match = $ui.SelectNodes('//node') | Where-Object { $_.text -eq $Label -or $_.'content-desc' -eq $Label } | Select-Object -First 1
    if (-not $match) { throw "No visible control: $Label" }
    $b = [regex]::Matches($match.bounds, '\d+')
    $x = [int](([int]$b[0].Value + [int]$b[2].Value) / 2)
    $y = [int](([int]$b[1].Value + [int]$b[3].Value) / 2)
    Invoke-TvAdb shell input tap $x $y
}
function Save-TvScreen([string]$Name) {
    $ui = Get-TvUi
    Invoke-TvAdb shell screencap -p /sdcard/lunvoryx-capture.png
    Invoke-TvAdb pull /sdcard/lunvoryx-capture.png (Join-Path $script:LunvoryxRoot "artifacts\$Name.png")
    $ui.Save((Join-Path $script:LunvoryxRoot "artifacts\$Name.xml"))
}
