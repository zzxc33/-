
$base = "http://localhost:8080"
$pass = 0; $fail = 0; $skip = 0
$results = @()

function Test-Rest($name, $method, $url, $body, $token, $expCode, $expBodyCode) {
    try {
        $headers = @{}
        if ($token) { $headers["Authorization"] = "Bearer $token" }
        $params = @{ Uri = $url; Method = $method; UseBasicParsing = $true; TimeoutSec = 10 }
        if ($body) { $params.ContentType = "application/json"; $params.Body = $body }
        if ($headers.Count -gt 0) { $params.Headers = $headers }
        $r = Invoke-WebRequest @params
        $httpOk = ($r.StatusCode -eq $expCode)
        $bodyOk = $true
        $bodyCode = $null
        if ($expBodyCode -ne $null -and $r.Content) {
            try {
                $j = $r.Content | ConvertFrom-Json
                $bodyCode = $j.code
                $bodyOk = ($bodyCode -eq $expBodyCode)
            } catch { $bodyOk = $false }
        }
        $ok = $httpOk -and $bodyOk
        if ($ok) { $script:pass++ } else { $script:fail++ }
        $results += [PSCustomObject]@{ Name=$name; HTTP=$r.StatusCode; BodyCode=$bodyCode; ExpHTTP=$expCode; ExpBody=$expBodyCode; Status=if($ok){"PASS"}else{"FAIL"} }
        return $r
    } catch {
        $httpCode = $_.Exception.Response.StatusCode.value__
        if (-not $httpCode) { $httpCode = "ERR" }
        $ok = ($httpCode -eq $expCode)
        if ($ok) { $script:pass++ } else { $script:fail++ }
        $results += [PSCustomObject]@{ Name=$name; HTTP=$httpCode; BodyCode=$null; ExpHTTP=$expCode; ExpBody=$expBodyCode; Status=if($ok){"PASS"}else{"FAIL"} }
        return $null
    }
}

function Show-Summary {
    ""
    Write-Host ("=" * 70)
    Write-Host "  API TEST REPORT"
    Write-Host ("=" * 70)
    foreach ($r in $results) {
        $icon = if ($r.Status -eq "PASS") { "OK" } else { "XX" }
        $bodyStr = if ($r.BodyCode -ne $null) { " body=$($r.BodyCode)" } else { "" }
        $expStr = if ($r.ExpBody -ne $null) { "(expect body=$($r.ExpBody))" } else { "" }
        Write-Host "[$icon] $($r.Name)"
        Write-Host "       HTTP=$($r.HTTP)/$($r.ExpHTTP)$bodyStr $expStr"
    }
    ""
    Write-Host ("-" * 70)
    Write-Host "  PASS: $pass   FAIL: $fail   SKIP: $skip   TOTAL: $($pass + $fail + $skip)"
    Write-Host ("=" * 70)
}

# ============ Phase 1: Public Pages ============
"=== Phase 1: Public Pages ==="
Test-Rest "GET /" "GET" "$base/" $null $null 200 $null | Out-Null
Test-Rest "GET /login" "GET" "$base/login" $null $null 200 $null | Out-Null
Test-Rest "GET /register" "GET" "$base/register" $null $null 200 $null | Out-Null
Test-Rest "GET /search" "GET" "$base/search" $null $null 200 $null | Out-Null
Test-Rest "GET /captcha (image)" "GET" "$base/captcha" $null $null 200 $null | Out-Null

# ============ Phase 2: Public REST API (HTTP 200 + body.code) ============
"=== Phase 2: Public REST API ==="
Test-Rest "GET /api/home" "GET" "$base/api/home" $null $null 200 0 | Out-Null
Test-Rest "GET /api/recommend?userId=0" "GET" "$base/api/recommend?userId=0&limit=5" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs page=0 size=5" "GET" "$base/api/songs?page=0&size=5" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/hot" "GET" "$base/api/songs/hot?limit=5" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/new" "GET" "$base/api/songs/new?limit=5" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/1 (exist)" "GET" "$base/api/songs/1" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/99999 (not exist)" "GET" "$base/api/songs/99999" $null $null 200 404 | Out-Null
Test-Rest "GET /api/songs/search keyword=周杰倫" "GET" "$base/api/songs/search?keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/search empty keyword" "GET" "$base/api/songs/search?keyword=" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/genres" "GET" "$base/api/songs/genres" $null $null 200 0 | Out-Null
Test-Rest "GET /api/songs/genre/pop" "GET" "$base/api/songs/genre/pop" $null $null 200 0 | Out-Null
Test-Rest "GET /api/playlists" "GET" "$base/api/playlists?limit=5" $null $null 200 0 | Out-Null
Test-Rest "GET /api/playlists/1" "GET" "$base/api/playlists/1" $null $null 200 0 | Out-Null
Test-Rest "GET /api/playlists/99999 (not exist)" "GET" "$base/api/playlists/99999" $null $null 200 404 | Out-Null
Test-Rest "GET /api/playlists/search" "GET" "$base/api/playlists/search?keyword=%E6%8E%A8%E8%8D%90" $null $null 200 0 | Out-Null
Test-Rest "GET /api/users/1/playlists" "GET" "$base/api/users/1/playlists" $null $null 200 0 | Out-Null
Test-Rest "GET /api/users/1/liked" "GET" "$base/api/users/1/liked" $null $null 200 0 | Out-Null
Test-Rest "POST /api/songs/1/play (no login)" "POST" "$base/api/songs/1/play?userId=0" $null $null 200 0 | Out-Null

# ============ Phase 3: Auth Flow ============
"=== Phase 3: Auth Flow ==="
$ts = Get-Date -Format "HHmmss"
$newUser = "qa_$ts"
$newPwd = "testpass123"
$bodyReg = @{ username=$newUser; password=$newPwd; email="${newUser}@test.com" } | ConvertTo-Json
$bodyLogin = @{ username=$newUser; password=$newPwd } | ConvertTo-Json

# 註冊 + 自動登入（成功）
$regResp = Test-Rest "POST /api/auth/register (success)" "POST" "$base/api/auth/register" $bodyReg $null 200 0
$token = $null; $userId = $null
if ($regResp -and $regResp.Content) {
    try {
        $rj = $regResp.Content | ConvertFrom-Json
        $token = $rj.data.token
        $userId = $rj.data.user.id
    } catch {}
}

# 重複註冊 → body.code=409
Test-Rest "POST /api/auth/register duplicate -> code=409" "POST" "$base/api/auth/register" $bodyReg $null 200 409 | Out-Null

# 短用戶名 → body.code=400
Test-Rest "POST /api/auth/register short username -> code=400" "POST" "$base/api/auth/register" (@{username="a";password="testpass123"} | ConvertTo-Json) $null 200 400 | Out-Null

# 登入成功
Test-Rest "POST /api/auth/login (success)" "POST" "$base/api/auth/login" $bodyLogin $null 200 0 | Out-Null

# 錯誤密碼 → body.code=401
Test-Rest "POST /api/auth/login wrong pwd -> code=401" "POST" "$base/api/auth/login" (@{username=$newUser;password="wrong"} | ConvertTo-Json) $null 200 401 | Out-Null

# /me 無 token → body.code=401
Test-Rest "GET /api/auth/me no token -> code=401" "GET" "$base/api/auth/me" $null $null 200 401 | Out-Null

# ============ Phase 4: Auth-Required API ============
"=== Phase 4: Auth-Required API ==="
if ($token) {
    Test-Rest "GET /api/auth/me with token" "GET" "$base/api/auth/me" $null $token 200 0 | Out-Null
    Test-Rest "POST /api/songs/1/like with token" "POST" "$base/api/songs/1/like?userId=$userId" $null $token 200 0 | Out-Null
    Test-Rest "POST /api/songs/1/play with token" "POST" "$base/api/songs/1/play?userId=$userId" $null $token 200 0 | Out-Null
    Test-Rest "POST /api/auth/logout" "POST" "$base/api/auth/logout" $null $token 200 0 | Out-Null
    # 登出後 /me → code=401
    Test-Rest "GET /api/auth/me after logout -> code=401" "GET" "$base/api/auth/me" $null $token 200 401 | Out-Null
} else {
    "  SKIP: no token"
    $skip += 5
}

# ============ Phase 5: Admin Pages ============
"=== Phase 5: Admin Pages ==="
Test-Rest "GET /admin/ dashboard" "GET" "$base/admin/" $null $null 200 $null | Out-Null
Test-Rest "GET /admin/songs" "GET" "$base/admin/songs" $null $null 200 $null | Out-Null
Test-Rest "GET /admin/users" "GET" "$base/admin/users" $null $null 200 $null | Out-Null
Test-Rest "GET /admin/recommend" "GET" "$base/admin/recommend" $null $null 200 $null | Out-Null

# ============ Phase 6: 特殊邊界 ============
"=== Phase 6: Edge Cases ==="
# 無 ID 參數的邊界（MusicApiController 的 songsByGenre 用 PathVariable，不存在 genre 也應 200）
Test-Rest "GET /api/songs/genre/nonexistent" "GET" "$base/api/songs/genre/nonexistent" $null $null 200 0 | Out-Null

# 推薦 limit 邊界
Test-Rest "GET /api/recommend limit=0" "GET" "$base/api/recommend?userId=0&limit=0" $null $null 200 0 | Out-Null
Test-Rest "GET /api/recommend limit=100" "GET" "$base/api/recommend?userId=0&limit=100" $null $null 200 0 | Out-Null

Show-Summary
