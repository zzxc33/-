[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Host "=== Performance Benchmark ==="

$l = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method POST -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 10
$tok = $l.data.token
Write-Host "Token OK"

function TestAvg($name, $url, $tokenVal) {
    $times = @()
    for ($i=0; $i -lt 10; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            if ($tokenVal) {
                Invoke-WebRequest -Uri $url -TimeoutSec 15 -UseBasicParsing -Headers @{Authorization="Bearer $tokenVal"} | Out-Null
            } else {
                Invoke-WebRequest -Uri $url -TimeoutSec 15 -UseBasicParsing | Out-Null
            }
        } catch {}
        $sw.Stop(); $times += $sw.ElapsedMilliseconds
    }
    $avg = [Math]::Round(($times | Measure-Object -Average).Average, 0)
    $max = ($times | Measure-Object -Maximum).Maximum
    Write-Host ("{0,-35} avg={1,5}ms  max={2,5}ms" -f $name, $avg, $max)
}

Write-Host "-- Cold Cache --"
TestAvg "GET /index" "http://localhost:8080/index" $null
TestAvg "GET /api/home" "http://localhost:8080/api/home?userId=1" $tok
TestAvg "GET /api/recommend?userId=1" "http://localhost:8080/api/recommend?userId=1&limit=12" $tok
TestAvg "GET /api/songs/hot" "http://localhost:8080/api/songs/hot?limit=10" $null
TestAvg "GET /api/ranking" "http://localhost:8080/api/ranking?limit=10" $null
TestAvg "GET /api/songs/search" "http://localhost:8080/api/songs/search?keyword=Queen" $null
TestAvg "GET /api/playlists/mine" "http://localhost:8080/api/playlists/mine" $tok

Write-Host ""
Write-Host "-- Warm Cache (2nd pass) --"
TestAvg "GET /api/home" "http://localhost:8080/api/home?userId=1" $tok
TestAvg "GET /api/recommend?userId=1" "http://localhost:8080/api/recommend?userId=1&limit=12" $tok
TestAvg "GET /api/songs/hot" "http://localhost:8080/api/songs/hot?limit=10" $null
TestAvg "GET /api/ranking" "http://localhost:8080/api/ranking?limit=10" $null
