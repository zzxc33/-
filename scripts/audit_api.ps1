[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Host "=== API TEST SUITE ==="

$allOk = $true
function QuickTest($name, $method, $url, $expect) {
    $script:allOk = $script:allOk
    try {
        $r = Invoke-WebRequest -Uri $url -Method $method -TimeoutSec 8 -UseBasicParsing -MaximumRedirection 0 -ErrorAction Stop
        $c = $r.StatusCode
    } catch {
        $c = $_.Exception.Response.StatusCode.value__
    }
    $icon = if ($c -eq $expect) { "✅" } else { "❌" }
    Write-Host "$icon $name → HTTP $c (expect $expect)"
    if ($c -ne $expect) { $script:allOk = $false }
    return ($c -eq $expect)
}

Write-Host "-- Public Pages --"
QuickTest "首页"   GET "http://localhost:8080/index" 200
QuickTest "登录页" GET "http://localhost:8080/login" 200
QuickTest "健康检查" GET "http://localhost:8080/actuator/health" 200

Write-Host "-- Public APIs (no auth) --"
QuickTest "热门歌曲" GET "http://localhost:8080/api/songs/hot?limit=5" 200
QuickTest "冷启动推荐 userId=0" GET "http://localhost:8080/api/recommend?userId=0&limit=5" 200
QuickTest "歌单列表" GET "http://localhost:8080/api/playlists" 200
QuickTest "搜索 Queen" GET "http://localhost:8080/api/songs/search?keyword=Queen" 200
QuickTest "排行榜" GET "http://localhost:8080/api/ranking?limit=10" 200
QuickTest "随机歌曲" GET "http://localhost:8080/api/songs/random?limit=5" 200

Write-Host "-- Protected Pages (should 302 to login) --"
QuickTest "每日推荐页" GET "http://localhost:8080/recommend/daily" 302
QuickTest "排行榜页"   GET "http://localhost:8080/ranking" 302
QuickTest "我的品味"   GET "http://localhost:8080/profile/stats" 302
QuickTest "歌单详情"   GET "http://localhost:8080/playlist/1" 302

Write-Host "-- Auth --"
$token = $null
try {
    $l = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method POST -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 10
    $token = $l.data.token
    Write-Host "✅ admin 登录 OK token=$($token.Substring(0,20))..."
} catch { Write-Host "❌ admin 登录失败: $_"; $allOk=$false }

if ($token) {
    Write-Host "-- Auth'd API --"
    $h = @{Authorization="Bearer $token"}
    try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/me' -Headers $h -TimeoutSec 5; Write-Host "✅ /auth/me → $($r.data.username)" } catch { Write-Host "❌ /auth/me 失败"; $allOk=$false }
    try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/recommend?userId=1&limit=10' -Headers $h -TimeoutSec 5; Write-Host "✅ 活跃推荐 → $($r.data.Count) 首歌" } catch { Write-Host "❌ 活跃推荐失败"; $allOk=$false }
    try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/playlists/mine' -Headers $h -TimeoutSec 5; Write-Host "✅ 我的歌单 → $($r.data.Count) 个" } catch { Write-Host "❌ 我的歌单失败"; $allOk=$false }
    try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/songs/1/play' -Method POST -Headers $h -TimeoutSec 5; Write-Host "✅ 播放记录" } catch { Write-Host "❌ 播放记录失败"; $allOk=$false }
    try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/songs/1/like' -Method POST -Headers $h -TimeoutSec 5; Write-Host "✅ 点赞" } catch { Write-Host "❌ 点赞失败"; $allOk=$false }
    try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/songs/1' -Headers $h -TimeoutSec 5; Write-Host "✅ 歌曲详情 → $($r.data.title)" } catch { Write-Host "❌ 歌曲详情失败"; $allOk=$false }
}

Write-Host "-- Edge Cases --"
QuickTest "不存在歌曲" GET "http://localhost:8080/api/songs/99999" 404
QuickTest "空关键词搜索" GET "http://localhost:8080/api/songs/search?keyword=" 200
QuickTest "Druid 监控" GET "http://localhost:8080/druid/index.html" 200

Write-Host ""
if ($allOk) { Write-Host "=== RESULT: ALL PASS 🎉 ===" } else { Write-Host "=== RESULT: SOME FAIL ❌ ===" }
