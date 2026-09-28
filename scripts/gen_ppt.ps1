# PowerShell: Generate 开题答辩 PPT via PowerPoint COM v3
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$outPath = 'd:\代码项目\毕业设计\defense\答辩PPT.pptx'
$ErrorActionPreference = 'Stop'

$BG_DARK = 0x1a1a2e
$BG_DEEP = 0x0f0f1a
$RED     = 0x6045E9   # e94560 BGR
$WHITE   = 0xFFFFFF
$GRAY    = 0xB8B8C8
$CYAN    = 0xE3C87E   # 7ec8e3 BGR
$ORANGE  = 0x6CB8FF   # ffB86c BGR
$GREEN   = 0x22C55E
$DARK    = 0x0a0a14

function Set-Bg($slide, $c) {
  $slide.FollowMasterBackground = $false
  $slide.Background.Fill.ForeColor.RGB = [int]$c
  $slide.Background.Fill.Solid()
}
function Tb($slide, $x, $y, $w, $h, $txt, $sz=16, $b=$false, $c=$WHITE, $a=1) {
  $fx=[float]$x; $fy=[float]$y; $fw=[float]$w; $fh=[float]$h
  $tb = $slide.Shapes.AddTextbox(1, $fx,$fy,$fw,$fh)
  $tb.TextFrame.TextRange.Text = [string]$txt
  $tb.TextFrame.TextRange.Font.Size = [float]$sz
  $tb.TextFrame.TextRange.Font.Bold = [int]$b
  $tb.TextFrame.TextRange.Font.Color.RGB = [int]$c
  $tb.TextFrame.TextRange.ParagraphFormat.Alignment = [int]$a
  $tb.TextFrame.WordWrap = $true
  try { $tb.TextFrame.TextRange.Font.NameFarEast = 'Microsoft YaHei' } catch {}
  try { $tb.TextFrame.TextRange.Font.Name = 'Segoe UI' } catch {}
  return $tb
}
function Sh($slide, $type, $x, $y, $w, $h, $fc, $lc=0, $lw=0) {
  $fx=[float]$x; $fy=[float]$y; $fw=[float]$w; $fh=[float]$h
  $s = $slide.Shapes.AddShape([int]$type, $fx,$fy,$fw,$fh)
  $s.Fill.ForeColor.RGB = [int]$fc
  if ($lw -gt 0) { $s.Line.ForeColor.RGB=[int]$lc; $s.Line.Weight=[float]$lw }
  else { $s.Line.Visible = $false }
  return $s
}

Write-Host 'Starting PowerPoint...'
$pp = New-Object -ComObject PowerPoint.Application
$pp.Visible = 1
$pres = $pp.Presentations.Add()
$pres.PageSetup.SlideWidth = 1280
$pres.PageSetup.SlideHeight = 720

# ========== SLIDE 1: COVER ==========
Write-Host 'Slide 1: Cover'
$s = $pres.Slides.Add(1, 12)
Set-Bg $s $BG_DEEP
Sh $s 1 0 0 1280 60 0x1a1a3e $RED 2
Sh $s 1 0 600 1280 4 0 $RED 2
Tb $s 80 220 1120 80 '音乐资源推荐与歌单管理系统' 44 $true $WHITE 2
Tb $s 80 310 1120 50 '—— 基于 Spring Boot 的混合推荐引擎与双端应用' 20 $false $GRAY 2
Sh $s 1 540 380 200 3 0 $RED 3
Tb $s 80 430 1120 36 '学生：李江超   |   软件工程专业' 16 $false $GRAY 2
Tb $s 80 460 1120 36 '指导教师：待填写' 16 $false $GRAY 2
Tb $s 80 520 1120 36 '南昌应用技术师范学院   ·   2027 届毕业设计开题答辩' 14 $false 0x888888 2

# ========== SLIDE 2: AGENDA ==========
Write-Host 'Slide 2: Agenda'
$s = $pres.Slides.Add(2, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 '开题答辩 · 8 个章节' 11 $true $RED 1
Tb $s 60 70 1160 60 '目录 · Agenda' 36 $true $WHITE 1
$ag = @('01  课题背景与研究意义','02  国内外研究现状','03  研究内容与目标','04  混合算法深度剖析','05  系统架构设计','06  数据库与 REST API','07  进度与已完成工作','08  总结与展望')
$y = 160
foreach ($item in $ag) {
  Sh $s 9 60 ($y+18) 12 12 $RED
  Tb $s 88 $y 1120 44 $item 20 $false $GRAY 1
  $y += 62
}

# ========== SLIDE 3: BACKGROUND ==========
Write-Host 'Slide 3: Background'
$s = $pres.Slides.Add(3, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 01 · 课题背景与研究意义' 11 $true $RED 1
Tb $s 60 70 1160 60 '为什么需要这个系统？' 36 $true $WHITE 1
Tb $s 60 160 600 30 '问题：信息过载' 22 $true $RED 1
Tb $s 60 195 600 120 '全球在线音乐订阅用户突破 5 亿，曲库规模数千万首。
传统排行榜只能反映热门但无法个性化；关键词搜索需要用户明确意图。
推荐系统在"不知道自己想听什么"时主动推送。' 15 $false $GRAY 1
Tb $s 60 320 600 30 '为什么我们做？' 22 $true $RED 1
Tb $s 60 355 600 150 '学术缺口：论文多聚焦算法精度，缺少完整工程实践。
毕设缺口：多数只做算法原型，无完整业务闭环。
工业缺口：单一算法各有缺陷，混合策略是必由之路。' 15 $false $GRAY 1
Tb $s 680 160 540 30 '三大核心创新点' 22 $true $RED 1
$inn = @(@('Innovation #1','动态权重混合引擎','根据用户交互数自动调整 CF/CB/热门/随机 四者权重，解决冷启动'),@('Innovation #2','双端统一后端','Thymeleaf Web + 微信小程序原生，共用同一套 Spring Boot REST API'),@('Innovation #3','推荐引擎诊断后台','实时查看三算法独立推荐结果 + 权重可视化 + Cold Start 识别'))
$y = 200
foreach ($i in $inn) {
  Sh $s 1 680 $y 540 110 0x141428 $RED 1.5
  Tb $s 695 ($y+10) 510 22 $i[0] 11 $true $RED 1
  Tb $s 695 ($y+30) 510 28 $i[1] 16 $true $WHITE 1
  Tb $s 695 ($y+62) 510 40 $i[2] 13 $false 0x9898b0 1
  $y += 125
}

# ========== SLIDE 4: ALGORITHMS ==========
Write-Host 'Slide 4: Algorithms'
$s = $pres.Slides.Add(4, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 02-04 · 核心算法' 11 $true $RED 1
Tb $s 60 70 1160 60 '三种推荐算法 · 深度剖析' 36 $true $WHITE 1
$tx=60; $ty=160
$col=@(200,440,260,130,130); $hd=@('算法模块','核心公式','输入数据','优点','缺点')
$cx=$tx; for($i=0;$i -lt 5;$i++){ Sh $s 1 $cx $ty $col[$i] 40 0x1f0f28; Tb $s ($cx+8) ($ty+10) ($col[$i]-16) 22 $hd[$i] 11 $true $RED 1; $cx += $col[$i] }
$ty += 40
$rows=@(@('CollaborativeRecommender','cos(u,v) = ru.rv / (||ru|| x ||rv||)','user_song_interactions 隐式评分','效果好 可发现新类型','冷启动 稀疏性'),@('ContentBasedRecommender','sim=0.5cos(Gs,Gu)+0.4cos(As,Au)+0.1cos(Bs,Bu)','songs.genre/artist/album','无冷启动 可解释','同质化 信息茧房'),@('HybridRecommenderService','final=a.CFnorm+b.CBnorm+g.Pop+d.Rand','两者融合 + 动态权重表','扬长避短 可诊断','复杂度略高'))
foreach ($r in $rows) {
  $cx=$tx
  for($i=0;$i -lt 5;$i++){ Sh $s 1 $cx $ty $col[$i] 50 0x0f0f1a; Tb $s ($cx+8) ($ty+10) ($col[$i]-16) 30 $r[$i] 11 $false $GRAY 1; $cx += $col[$i] }
  $ty += 50
}

# ========== SLIDE 5: WEIGHT STRATEGY ==========
Write-Host 'Slide 5: Weights'
$s = $pres.Slides.Add(5, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 04 · 核心创新' 11 $true $RED 1
Tb $s 60 70 1160 60 '动态权重融合策略' 36 $true $WHITE 1
Tb $s 60 150 1160 30 '权重根据用户 interactionCount（交互记录数）自动调整，解决冷启动问题' 14 $false $GRAY 1
$bars=@(@('❄️ 冷启动（0 条）',0,0,50,50,'COLD',$ORANGE),@('稀疏（≤5 条）',20,50,20,10,'SPARSE',$ORANGE),@('🔥 活跃（>5 条）',50,20,15,15,'NORMAL',$CYAN))
$by=200
foreach($b in $bars){
  Tb $s 60 $by 220 24 $b[0] 14 $false $WHITE 1
  Sh $s 1 290 ($by+2) 800 22 0x1a1a2e
  $bx=292; $segW=7.96
  if($b[1] -gt 0){ Sh $s 1 $bx ($by+2) ($b[1]*$segW) 22 $RED; Tb $s ($bx+4) ($by+4) ($b[1]*$segW) 18 "CF $($b[1])%" 9 $true $WHITE 1; $bx += ($b[1]*$segW) }
  if($b[2] -gt 0){ Sh $s 1 $bx ($by+2) ($b[2]*$segW) 22 $ORANGE; Tb $s ($bx+4) ($by+4) ($b[2]*$segW) 18 "CB $($b[2])%" 9 $true 0x000000 1; $bx += ($b[2]*$segW) }
  if($b[3] -gt 0){ Sh $s 1 $bx ($by+2) ($b[3]*$segW) 22 $CYAN; Tb $s ($bx+4) ($by+4) ($b[3]*$segW) 18 "热门 $($b[3])%" 9 $true 0x000000 1; $bx += ($b[3]*$segW) }
  if($b[4] -gt 0){ Sh $s 1 $bx ($by+2) ($b[4]*$segW) 22 0x404060; Tb $s ($bx+4) ($by+4) ($b[4]*$segW) 18 "随机 $($b[4])%" 9 $true $WHITE 1 }
  Sh $s 1 1110 $by 80 26 0x2a1a2e $b[6] 1
  Tb $s 1114 ($by+3) 72 20 $b[5] 11 $true $b[6] 2
  $by += 45
}
Tb $s 60 370 760 28 '融合逻辑（HybridRecommenderService.java）' 14 $true $RED 1
$code = "long cnt = repo.countByUserId(userId);`nif (cnt == 0) return hotSongs + randomFill;`ndouble cfW, cbW, popW, randW;`nif (cnt <= 5) {`n  cfW=0.20; cbW=0.50; popW=0.20; randW=0.10;`n} else {`n  cfW=0.50; cbW=0.20; popW=0.15; randW=0.15;`n}`nreturn cfW*CF_n + cbW*CB_n + popW*pop + randW*rand;"
Sh $s 1 60 408 760 260 $DARK $RED 1.5
Tb $s 80 416 720 244 $code 11 $false 0xD4D4E0 1

# ========== SLIDE 6: ARCHITECTURE ==========
Write-Host 'Slide 6: Architecture'
$s = $pres.Slides.Add(6, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 05 · 系统架构' 11 $true $RED 1
Tb $s 60 70 1160 60 '分层架构 · 双端统一后端' 36 $true $WHITE 1

# Frontend
Tb $s 60 150 580 24 '📱 前端层（双端）' 12 $true $RED 1
Sh $s 1 60 180 270 40 0x1a1a3e $RED 1; Tb $s 70 188 250 24 'Web 端 · Thymeleaf + 原生 JS' 11 $false $WHITE 1
Sh $s 1 370 180 270 40 0x1a1a3e $RED 1; Tb $s 380 188 250 24 '微信小程序 · WXML/WXSS/JS 原生' 11 $false $WHITE 1
Tb $s 340 225 200 20 '⬇ REST API · /api/** · Token' 11 $false $RED 2

# Controller
Tb $s 60 250 580 24 '🎛️ Controller 层（8 个）' 12 $true $CYAN 1
Sh $s 1 60 280 580 60 0x101a2e
Tb $s 72 288 560 20 'Home · Song · Playlist · Login' 10 $false $GRAY 1
Tb $s 72 310 560 20 'MusicApi · AuthApi · Admin · AdminRecommend' 10 $true $RED 1
Tb $s 340 345 200 20 '⬇' 16 $true $RED 2

# Service
Tb $s 60 370 580 24 '🧠 Service 层 + 推荐引擎' 12 $true $RED 1
Sh $s 1 60 400 580 90 0x10102e
Tb $s 72 408 560 20 'SongService · PlaylistService · UserService' 10 $false $GRAY 1
Sh $s 1 72 432 556 52 0x1a0a1e $RED 1.5
Tb $s 84 440 530 20 '⚙️ HybridRecommenderService（红框高亮）' 11 $true $WHITE 1
Tb $s 84 460 530 20 'CF · CB · Hot + Random 三算法并行' 9 $false $RED 1
Tb $s 340 495 200 20 '⬇' 16 $true $CYAN 2

# JPA
Tb $s 60 520 580 24 '🔗 Spring Data JPA' 12 $true $CYAN 1
Sh $s 1 60 550 580 35 0x102035 $CYAN 1
Tb $s 70 558 560 20 'Repository 模式 · @Query · 6 张表' 10 $false $CYAN 1
Tb $s 340 590 200 20 '⬇' 16 $true $CYAN 2

# DB
Tb $s 60 610 580 24 '🗄️ MySQL 8.0' 12 $true $GREEN 1
Sh $s 1 60 625 580 35 0x10281a $GREEN 1
Tb $s 70 632 560 20 '70 songs · 4 users · 55 interactions' 10 $false $GREEN 1

# Right tech cards
Tb $s 700 150 520 24 '🛠️ 技术栈明细' 18 $true $WHITE 1
$tech=@(@('Backend','Spring Boot 3.2.12','Security+JPA+Kaptcha'),@('Database','MySQL 8.0','6 tables'),@('Web','Thymeleaf + 原生 JS','14 templates'),@('Mini','微信原生框架','6 pages'),@('Auth','简化 Token','内存·7天·Bearer'),@('Build','Maven + Lombok','Java 22'))
$ty=180
foreach($t in $tech){
  Sh $s 1 700 $ty 260 58 0x141428
  Tb $s 710 ($ty+4) 240 14 $t[0] 9 $true $RED 1
  Tb $s 710 ($ty+20) 240 18 $t[1] 13 $true $WHITE 1
  Tb $s 710 ($ty+38) 240 16 $t[2] 10 $false $GRAY 1
  Sh $s 1 970 $ty 250 58 0x141428
  Tb $s 980 ($ty+4) 230 14 $t[0] 9 $true $RED 1
  Tb $s 980 ($ty+20) 230 18 $t[1] 13 $true $WHITE 1
  Tb $s 980 ($ty+38) 230 16 $t[2] 10 $false $GRAY 1
  $ty += 68
}

# ========== SLIDE 7: DATABASE ==========
Write-Host 'Slide 7: Database'
$s = $pres.Slides.Add(7, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 06 · 数据库设计' 11 $true $RED 1
Tb $s 60 70 1160 60 '6 张核心表 · ER 关系' 36 $true $WHITE 1
$tx=60; $ty=160; $col=@(180,80,400,220,280); $hd=@('表名','字段数','关键字段','用途','关系')
$cx=$tx; for($i=0;$i -lt 5;$i++){ Sh $s 1 $cx $ty $col[$i] 40 0x1f0f28; Tb $s ($cx+8) ($ty+10) ($col[$i]-16) 22 $hd[$i] 11 $true $RED 1; $cx += $col[$i] }
$ty += 40
$rows=@(@('users','10','id, username, password, email, role','用户账户 + 权限','1→N playlists, interactions'),@('songs','12','id, title, artist, album, genre, cover_url','歌曲元数据','被 playlist_songs, interactions 引用'),@('playlists','8','id, name, user_id, is_public, cover_url','歌单','N→1 users, 1→N playlist_songs'),@('playlist_songs','6','playlist_id, song_id, sort_order','歌单-歌曲关联','M→M 中间表'),@('user_song_interactions','8','user_id, song_id, play_count, is_liked','推荐引擎核心输入','用户-物品交互矩阵'),@('comments','6','song_id, user_id, content, like_count','评论区','N→1 songs, users'))
foreach($r in $rows){ $cx=$tx; for($i=0;$i -lt 5;$i++){ Sh $s 1 $cx $ty $col[$i] 40 0x0f0f1a; Tb $s ($cx+8) ($ty+8) ($col[$i]-16) 24 $r[$i] 10 $false $GRAY 1; $cx += $col[$i] }; $ty += 40 }
Tb $s 60 610 1160 24 '📋 DataInitializer 自动填充' 12 $true $RED 1
Tb $s 60 632 1160 50 '70 首歌曲（10 种风格 x 7 首）· 4 个用户（admin + 3 普通）· 55 条交互（用户 1-2-3 之间 5-7 首重叠，制造协同过滤相似用户）' 11 $false $GRAY 1

# ========== SLIDE 8: REST API ==========
Write-Host 'Slide 8: REST API'
$s = $pres.Slides.Add(8, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 06 · REST API 设计' 11 $true $RED 1
Tb $s 60 70 1160 60 '17 个 REST 端点 · Token 认证' 36 $true $WHITE 1
Tb $s 60 150 560 24 '🔑 认证 API' 14 $true $RED 1
$ry=180; $auth=@(@('POST','/api/auth/login','登录 → 返回 token + user'),@('POST','/api/auth/register','注册 → 自动登录'),@('POST','/api/auth/logout','登出'),@('GET','/api/auth/me','获取当前用户'))
foreach($a in $auth){ Sh $s 1 60 $ry 560 28 0x0f0f1a; $mc=if($a[0] -eq 'POST'){$GREEN}else{$CYAN}; Tb $s 70 ($ry+4) 70 20 $a[0] 10 $true $mc 1; Tb $s 140 ($ry+4) 240 20 $a[1] 10 $false $WHITE 1; Tb $s 380 ($ry+4) 220 20 $a[2] 10 $false $GRAY 1; $ry += 32 }
Tb $s 660 150 560 24 '🎵 核心 API（MusicApiController）' 14 $true $RED 1
$ry=180; $music=@(@('GET','/api/home?userId=','首页聚合（4合1）'),@('GET','/api/recommend','混合推荐'),@('GET','/api/songs/hot|search|genres','歌曲浏览'),@('GET','/api/songs/{id}','详情 + 相关推荐'),@('POST','/api/songs/{id}/play|like','交互记录'),@('GET','/api/users/{id}/liked','喜欢列表'))
foreach($a in $music){ Sh $s 1 660 $ry 560 28 0x0f0f1a; $mc=if($a[0] -eq 'POST'){$GREEN}else{$CYAN}; Tb $s 670 ($ry+4) 70 20 $a[0] 10 $true $mc 1; Tb $s 740 ($ry+4) 240 20 $a[1] 10 $false $WHITE 1; Tb $s 900 ($ry+4) 220 20 $a[2] 10 $false $GRAY 1; $ry += 32 }
Tb $s 60 380 560 24 '🔧 推荐引擎诊断 API' 14 $true $RED 1
$ry=410; $adm=@(@('/admin/api/recommend/stats','全局统计 + 风格分布'),@('/admin/api/recommend/diagnose/{uid}','三算法独立推荐 + 用户阶段'),@('/admin/api/recommend/hybrid','混合推荐最终结果'),@('/admin/api/recommend/cf','单独协同过滤'),@('/admin/api/recommend/cb','单独内容推荐'))
foreach($a in $adm){ Sh $s 1 60 $ry 560 28 0x0f0f1a; Tb $s 70 ($ry+4) 280 20 $a[0] 10 $false $CYAN 1; Tb $s 360 ($ry+4) 240 20 $a[1] 10 $false $GRAY 1; $ry += 32 }
Sh $s 1 660 380 560 160 $DARK $RED 1.5
Tb $s 680 392 520 20 '🔐 认证与响应格式' 13 $true $RED 1
Tb $s 680 414 520 110 'Authorization: Bearer 0dc0a1d1fdc247f3b0d842ea574d3908`n`nApiResponse { code: 0|401|404, msg: string, data: any }`n`nSecurityConfig: /api/** permitAll + CORS + ignore CSRF' 11 $false 0xD4D4E0 1

# ========== SLIDE 9: PROGRESS ==========
Write-Host 'Slide 9: Progress'
$s = $pres.Slides.Add(9, 12)
Set-Bg $s $BG_DARK
Tb $s 60 40 600 30 'Chapter 07 · 进度安排' 11 $true $RED 1
Tb $s 60 70 1160 60 '16 周毕业设计周期 · 已完成 11 周' 36 $true $WHITE 1
$tl=@(@('第 1-3 周','✅',$true,'文献调研 · 开题报告'),@('第 4-6 周','✅',$true,'Spring Boot 骨架 · 数据库 6 张表 · Security + JPA'),@('第 7-9 周','✅',$true,'协同过滤 UserCF · 内容推荐 CB · 混合引擎'),@('第 10-11 周','✅',$true,'歌单/评论/搜索 · Thymeleaf 14 模板 · REST API 17 端点'),@('第 12-13 周','🔄',$false,'算法效果评估 · 功能测试 · Bug 修复'),@('第 14-16 周','📝',$false,'毕业论文撰写 · 答辩 PPT · 系统演示'))
$ty=160
foreach($t in $tl){ $bc=if($t[2]){$GREEN}else{$RED}; Sh $s 9 60 ($ty+8) 12 12 $bc; Tb $s 84 $ty 200 24 "$($t[1]) $($t[0])" 13 $true $bc 1; Tb $s 84 ($ty+22) 1120 24 $t[3] 13 $false $GRAY 1; $ty += 62 }
Tb $s 800 150 420 24 '📦 已完成交付物' 18 $true $WHITE 1
$dy=185
$dl=@(@('Backend','12 个 Java 文件','3 REST + 3 Ctrl + SecurityConfig'),@('Web Frontend','14 Thymeleaf 模板','6 fragment + Player'),@('Mini Program','6 页面 + request.js','原生 WXML/WXSS/JS'),@('Database','70 songs·4 users·55 ints','DataInitializer 自动填充'))
foreach($d in $dl){ Sh $s 1 800 $dy 420 50 0x141428; Tb $s 812 ($dy+4) 400 14 $d[0] 9 $true $RED 1; Tb $s 812 ($dy+18) 400 16 $d[1] 12 $true $WHITE 1; Tb $s 812 ($dy+34) 400 14 $d[2] 9 $false $GRAY 1; $dy += 58 }
Tb $s 800 420 420 24 '⚠️ 剩余风险' 14 $true $RED 1
Tb $s 800 444 420 120 '• 算法评估：需接入公开数据集做对比实验`n• 真机调试：小程序需配真机 URL`n• 论文：按学校格式 + 查重 ≤ 15%' 12 $false $GRAY 1

# ========== SLIDE 10: Q&A ==========
Write-Host 'Slide 10: Q&A'
$s = $pres.Slides.Add(10, 12)
Set-Bg $s $BG_DEEP
Sh $s 1 0 0 1280 720 0x0a0a14
Tb $s 0 200 1280 100 '🎤' 80 $true $WHITE 2
Tb $s 0 340 1280 80 '感谢各位老师！' 48 $true $WHITE 2
Tb $s 0 430 1280 40 '欢迎提问 · Q & A' 22 $false $GRAY 2
Sh $s 1 440 490 400 3 0 $RED 2; Sh $s 1 840 490 400 3 0 $RED 2
Tb $s 0 530 1280 30 '🔗 Spring Boot Backend   ·   📱 微信小程序   ·   🧠 混合推荐引擎' 13 $false 0x666666 2

# ========== SAVE ==========
Write-Host 'Saving...'
$pres.SaveAs($outPath, 1)
$pres.Close()
$pp.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($pres) | Out-Null
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($pp) | Out-Null
[GC]::Collect(); [GC]::WaitForPendingFinalizers()
Write-Host "✅ Done: $outPath"
