-- ============================================================
-- 數據庫初始化腳本（Spring Boot 每次啟動執行）
-- 歌單種子化由 DataInitializer (CommandLineRunner) 負責
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    nickname VARCHAR(50),
    role VARCHAR(20) DEFAULT 'ROLE_USER',
    favorite_genre VARCHAR(100),
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS songs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    artist VARCHAR(200) NOT NULL,
    album VARCHAR(200),
    cover_url VARCHAR(500),
    audio_url VARCHAR(500),
    genre VARCHAR(50),
    duration VARCHAR(20),
    play_count BIGINT DEFAULT 0,
    like_count BIGINT DEFAULT 0,
    description TEXT,
    created_at DATETIME,
    UNIQUE KEY idx_song_title_artist (title(100), artist(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS playlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    cover_url VARCHAR(500),
    user_id BIGINT NOT NULL,
    is_public BOOLEAN DEFAULT TRUE,
    play_count BIGINT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS playlist_songs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    playlist_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    sort_order INT DEFAULT 0,
    added_at DATETIME,
    UNIQUE KEY uk_playlist_song (playlist_id, song_id),
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_song_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    play_count INT DEFAULT 0,
    is_liked BOOLEAN DEFAULT FALSE,
    last_played_at DATETIME,
    liked_at DATETIME,
    created_at DATETIME,
    UNIQUE KEY uk_user_song (user_id, song_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    song_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    like_count BIGINT DEFAULT 0,
    created_at DATETIME,
    FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. INSERT IGNORE 種子數據
-- 用戶由 DataInitializer (CommandLineRunner) 在應用啟動後自動創建
-- 歌曲（70 首）
INSERT IGNORE INTO songs (title, artist, album, genre, duration, play_count, like_count, cover_url, audio_url, description, created_at) VALUES
('晴天', '周杰伦', '叶惠美', '流行', '04:29', 15200, 8900, 'https://picsum.photos/seed/song1/300/300', '/audio/sample1.mp3', '经典华语流行歌曲', NOW()),
('夜曲', '周杰伦', '十一月的肖邦', '流行', '03:48', 14300, 8200, 'https://picsum.photos/seed/song2/300/300', '/audio/sample2.mp3', '周杰伦经典作品', NOW()),
('匆匆那年', '王菲', '匆匆那年', '流行', '04:15', 12000, 7500, 'https://picsum.photos/seed/song3/300/300', '/audio/sample3.mp3', '电影《匆匆那年》主题曲', NOW()),
('光年之外', '邓紫棋', '光年之外', '流行', '03:55', 13500, 7800, 'https://picsum.photos/seed/song4/300/300', '/audio/sample4.mp3', '邓紫棋代表作', NOW()),
('成都', '赵雷', '成都', '民谣', '05:28', 11000, 6900, 'https://picsum.photos/seed/song5/300/300', '/audio/sample5.mp3', '民谣经典', NOW()),
('平凡之路', '朴树', '猎户星座', '民谣', '05:02', 9800, 6500, 'https://picsum.photos/seed/song6/300/300', '/audio/sample6.mp3', '朴树经典作品', NOW()),
('海阔天空', 'Beyond', '乐与怒', '摇滚', '05:24', 16800, 9200, 'https://picsum.photos/seed/song7/300/300', '/audio/sample7.mp3', 'Beyond经典摇滚', NOW()),
('光辉岁月', 'Beyond', '命运派对', '摇滚', '04:58', 14500, 8500, 'https://picsum.photos/seed/song8/300/300', '/audio/sample8.mp3', 'Beyond代表作', NOW()),
('Mojito', '周杰伦', 'Mojito', '流行', '03:05', 8900, 5600, 'https://picsum.photos/seed/song9/300/300', '/audio/sample9.mp3', '周杰伦拉丁风', NOW()),
('孤勇者', '陈奕迅', '孤勇者', '流行', '04:18', 17500, 9500, 'https://picsum.photos/seed/song10/300/300', '/audio/sample10.mp3', '陈奕迅热门单曲', NOW()),
('起风了', '买辣椒也用券', '起风了', '流行', '04:26', 10200, 6200, 'https://picsum.photos/seed/song11/300/300', '/audio/sample11.mp3', '日文歌曲中文翻唱', NOW()),
('赤伶', 'HITA', '赤伶', '古风', '04:12', 7800, 5100, 'https://picsum.photos/seed/song12/300/300', '/audio/sample12.mp3', '古风歌曲', NOW()),
('贝加尔湖畔', '李健', '依然', '民谣', '04:05', 8700, 5400, 'https://picsum.photos/seed/song13/300/300', '/audio/sample13.mp3', '李健经典民谣', NOW()),
('泡沫', '邓紫棋', 'Xposed', '流行', '04:18', 12500, 7200, 'https://picsum.photos/seed/song14/300/300', '/audio/sample14.mp3', '邓紫棋成名曲', NOW()),
('稻香', '周杰伦', '魔杰座', '流行', '03:43', 13800, 8000, 'https://picsum.photos/seed/song15/300/300', '/audio/sample15.mp3', '周杰伦田园风', NOW()),
('天空之城', '久石让', '天空之城', '轻音乐', '04:30', 9500, 5800, 'https://picsum.photos/seed/song16/300/300', '/audio/sample16.mp3', '宫崎骏动画配乐', NOW()),
('七里香', '周杰伦', '七里香', '流行', '04:59', 16000, 9100, 'https://picsum.photos/seed/song17/300/300', '/audio/sample1.mp3', '周杰伦经典夏日情歌', NOW()),
('青花瓷', '周杰伦', '我很忙', '古风', '03:57', 15500, 8800, 'https://picsum.photos/seed/song18/300/300', '/audio/sample2.mp3', '中国风代表之作', NOW()),
('十年', '陈奕迅', '黑白灰', '流行', '03:24', 14800, 8600, 'https://picsum.photos/seed/song19/300/300', '/audio/sample3.mp3', '陈奕迅经典情歌', NOW()),
('浮夸', '陈奕迅', 'U-87', '流行', '04:46', 13200, 7800, 'https://picsum.photos/seed/song20/300/300', '/audio/sample4.mp3', '陈奕迅代表作', NOW()),
('南山南', '马頔', '南山南', '民谣', '04:20', 10800, 6400, 'https://picsum.photos/seed/song21/300/300', '/audio/sample5.mp3', '民谣经典之作', NOW()),
('董小姐', '宋冬野', '董小姐', '民谣', '04:13', 9200, 5800, 'https://picsum.photos/seed/song22/300/300', '/audio/sample6.mp3', '民谣代表作', NOW()),
('夜空中最亮的星', '逃跑计划', '世界', '摇滚', '04:11', 14000, 8300, 'https://picsum.photos/seed/song23/300/300', '/audio/sample7.mp3', '国内摇滚经典', NOW()),
('无地自容', '黑豹乐队', '黑豹', '摇滚', '05:37', 12500, 7200, 'https://picsum.photos/seed/song24/300/300', '/audio/sample8.mp3', '中国摇滚里程碑', NOW()),
('Shape of You', 'Ed Sheeran', '÷', '流行', '03:54', 20000, 12000, 'https://picsum.photos/seed/song25/300/300', '/audio/sample9.mp3', '全球热单', NOW()),
('See You Again', 'Wiz Khalifa', '速度与激情7', '流行', '03:52', 18500, 11000, 'https://picsum.photos/seed/song26/300/300', '/audio/sample10.mp3', '致敬保罗·沃克', NOW()),
('Counting Stars', 'OneRepublic', 'Native', '流行', '04:17', 17000, 9800, 'https://picsum.photos/seed/song27/300/300', '/audio/sample11.mp3', '流行摇滚热单', NOW()),
('Apologize', 'OneRepublic', 'Dreaming Out Loud', '流行', '03:28', 15500, 8600, 'https://picsum.photos/seed/song28/300/300', '/audio/sample12.mp3', '经典流行摇滚', NOW()),
('东风破', '周杰伦', '叶惠美', '古风', '05:13', 15300, 8700, 'https://picsum.photos/seed/song29/300/300', '/audio/sample1.mp3', '中国风开山之作', NOW()),
('烟花易冷', '周杰伦', '跨时代', '古风', '04:22', 11800, 7200, 'https://picsum.photos/seed/song30/300/300', '/audio/sample2.mp3', '周杰伦中国风', NOW()),
('嘉宾', '张远', '嘉宾', '流行', '04:21', 11200, 6700, 'https://picsum.photos/seed/song31/300/300', '/audio/sample3.mp3', '热门情歌', NOW()),
('后来', '刘若英', '我等你', '流行', '04:38', 13800, 7900, 'https://picsum.photos/seed/song32/300/300', '/audio/sample4.mp3', '经典流行情歌', NOW()),
('吻别', '张学友', '吻别', '流行', '05:07', 14200, 8100, 'https://picsum.photos/seed/song33/300/300', '/audio/sample5.mp3', '歌神经典之作', NOW()),
('以父之名', '周杰伦', '叶惠美', '流行', '05:42', 12800, 7600, 'https://picsum.photos/seed/song34/300/300', '/audio/sample6.mp3', '周杰伦暗黑风格代表作', NOW()),
('Steps of Chaos', 'Jin Goo', 'Echoes', '电子', '03:45', 7200, 4200, 'https://picsum.photos/seed/song35/300/300', '/audio/sample7.mp3', '电子音乐精选', NOW()),
('清明雨上', '许嵩', '自定义', '古风', '04:21', 10200, 6100, 'https://picsum.photos/seed/song36/300/300', '/audio/sample8.mp3', '许嵩中国风代表作', NOW()),
('庐州月', '许嵩', '寻雾启示', '古风', '04:22', 9500, 5700, 'https://picsum.photos/seed/song37/300/300', '/audio/sample9.mp3', '许嵩古风经典', NOW()),
('少女的祈祷', '杨丞琳', '遇上爱', '流行', '04:14', 10500, 6100, 'https://picsum.photos/seed/song38/300/300', '/audio/sample10.mp3', '经典华语情歌', NOW()),
('Concerto for 2 Violins', 'Bach', 'Classical Masterpieces', '古典', '05:20', 5800, 3500, 'https://picsum.photos/seed/song39/300/300', '/audio/sample1.mp3', '巴赫经典室内乐', NOW()),
('Canon in D', 'Pachelbel', 'Classical Collection', '古典', '04:58', 8800, 5200, 'https://picsum.photos/seed/song40/300/300', '/audio/sample2.mp3', '最经典的卡农', NOW()),
('说好不哭', '周杰伦', '说好不哭', '流行', '03:42', 15800, 9200, 'https://picsum.photos/seed/song41/300/300', '/audio/sample3.mp3', '周杰伦阿信合作单曲', NOW()),
('等你下课', '周杰伦', '等你下课', '流行', '04:30', 14500, 8500, 'https://picsum.photos/seed/song42/300/300', '/audio/sample4.mp3', '周杰伦校园风', NOW()),
('怪美的', '蔡依林', 'Ugly Beauty', '流行', '03:55', 11200, 6800, 'https://picsum.photos/seed/song43/300/300', '/audio/sample5.mp3', '蔡依林金曲奖专辑', NOW()),
('大艺术家', '蔡依林', 'Muse', '流行', '03:20', 10800, 6400, 'https://picsum.photos/seed/song44/300/300', '/audio/sample6.mp3', '蔡依林经典舞曲', NOW()),
('凉凉', '张碧晨', '三生三世十里桃花', '古风', '05:05', 13500, 7800, 'https://picsum.photos/seed/song45/300/300', '/audio/sample7.mp3', '三生三世主题曲', NOW()),
('消愁', '毛不易', '平凡的一天', '民谣', '04:20', 14200, 8100, 'https://picsum.photos/seed/song46/300/300', '/audio/sample8.mp3', '毛不易成名之作', NOW()),
('像我这样的人', '毛不易', '平凡的一天', '民谣', '04:02', 13200, 7600, 'https://picsum.photos/seed/song47/300/300', '/audio/sample9.mp3', '毛不易代表作', NOW()),
('如果有一天', '梁静茹', '丝路', '流行', '04:26', 11500, 6700, 'https://picsum.photos/seed/song48/300/300', '/audio/sample10.mp3', '梁静茹治愈情歌', NOW()),
('勇气', '梁静茹', '勇气', '流行', '03:58', 12800, 7400, 'https://picsum.photos/seed/song49/300/300', '/audio/sample11.mp3', '梁静茹经典', NOW()),
('挪威的森林', '伍佰', '爱情的尽头', '摇滚', '04:24', 14000, 8000, 'https://picsum.photos/seed/song50/300/300', '/audio/sample12.mp3', '伍佰经典摇滚情歌', NOW()),
('突然的自我', '伍佰', '无悔', '摇滚', '03:44', 13500, 7700, 'https://picsum.photos/seed/song51/300/300', '/audio/sample1.mp3', '伍佰代表作', NOW()),
('Blinding Lights', 'The Weeknd', 'After Hours', '流行', '03:20', 21000, 13000, 'https://picsum.photos/seed/song52/300/300', '/audio/sample2.mp3', '全球热单', NOW()),
('Starboy', 'The Weeknd', 'Starboy', '流行', '03:50', 19000, 11000, 'https://picsum.photos/seed/song53/300/300', '/audio/sample3.mp3', 'The Weeknd经典', NOW()),
('Bohemian Rhapsody', 'Queen', 'A Night at the Opera', '摇滚', '05:55', 22000, 14500, 'https://picsum.photos/seed/song54/300/300', '/audio/sample4.mp3', '皇后乐队传世经典', NOW()),
('Hotel California', 'Eagles', 'Hotel California', '摇滚', '06:30', 21500, 14000, 'https://picsum.photos/seed/song55/300/300', '/audio/sample5.mp3', '老鹰乐队不朽名作', NOW()),
('Yesterday', 'The Beatles', 'Help!', '摇滚', '02:05', 20500, 12500, 'https://picsum.photos/seed/song56/300/300', '/audio/sample6.mp3', '披头士经典', NOW()),
('告白气球', '周杰伦', '周杰伦的床边故事', '流行', '03:35', 16500, 9500, 'https://picsum.photos/seed/song57/300/300', '/audio/sample7.mp3', '周杰伦浪漫情歌', NOW()),
('童话', '光良', '童话', '流行', '04:26', 12500, 7100, 'https://picsum.photos/seed/song58/300/300', '/audio/sample8.mp3', '经典华语情歌', NOW()),
('Our Song', 'Taylor Swift', 'Fearless', '流行', '03:21', 16500, 9800, 'https://picsum.photos/seed/song59/300/300', '/audio/sample9.mp3', 'Taylor Swift乡村流行', NOW()),
('Love Story', 'Taylor Swift', 'Fearless', '流行', '03:55', 18000, 10500, 'https://picsum.photos/seed/song60/300/300', '/audio/sample10.mp3', 'Taylor Swift经典', NOW()),
('星晴', '周杰伦', 'Jay', '流行', '04:18', 13000, 7500, 'https://picsum.photos/seed/song61/300/300', '/audio/sample11.mp3', '周杰伦首张专辑经典', NOW()),
('曹操', '林俊杰', '曹操', '流行', '04:38', 12800, 7300, 'https://picsum.photos/seed/song62/300/300', '/audio/sample12.mp3', '林俊杰中国风', NOW()),
('江南', '林俊杰', '第二天堂', '流行', '04:22', 14000, 8000, 'https://picsum.photos/seed/song63/300/300', '/audio/sample1.mp3', '林俊杰成名曲', NOW()),
('不为谁而作的歌', '林俊杰', '和自己对话', '流行', '04:25', 13500, 7800, 'https://picsum.photos/seed/song64/300/300', '/audio/sample2.mp3', '林俊杰金曲奖作品', NOW()),
('修炼爱情', '林俊杰', '因你而在', '流行', '04:17', 12500, 7200, 'https://picsum.photos/seed/song65/300/300', '/audio/sample3.mp3', '林俊杰经典情歌', NOW()),
('反方向的钟', '周杰伦', 'Jay', '流行', '04:10', 10800, 6200, 'https://picsum.photos/seed/song66/300/300', '/audio/sample4.mp3', '周杰伦早期代表作', NOW()),
('Spring Snow', 'Yiruma', 'The Best of Yiruma', '轻音乐', '03:45', 6800, 4100, 'https://picsum.photos/seed/song67/300/300', '/audio/sample5.mp3', '李闰珉钢琴曲', NOW()),
('River Flows in You', 'Yiruma', 'First Love', '轻音乐', '04:15', 10200, 6200, 'https://picsum.photos/seed/song68/300/300', '/audio/sample6.mp3', '最经典钢琴曲之一', NOW()),
('紫色激情', '李贞贤', '激怒', '电子', '03:28', 8500, 4900, 'https://picsum.photos/seed/song69/300/300', '/audio/sample7.mp3', '经典电子舞曲', NOW()),
('Change', 'Hyun A', 'Melting', '流行', '03:30', 7800, 4500, 'https://picsum.photos/seed/song70/300/300', '/audio/sample8.mp3', '泫雅人气单曲', NOW());

-- 評論示例
INSERT IGNORE INTO comments (song_id, user_id, content, like_count, created_at) VALUES
(1, 1, '这首歌的旋律真的太治愈了！每次听到都会想起大学的时光 ❤️', 128, NOW() - INTERVAL 2 DAY),
(1, 1, '基于协同过滤的推荐果然靠谱，这首之后推荐的几首我都很喜欢！', 56, NOW() - INTERVAL 5 DAY),
(1, 1, '编曲太精致了，副歌部分直接泪目。已加入循环歌单 🎧', 89, NOW() - INTERVAL 7 DAY);

-- 歌單種子化由 DataInitializer 負責（更可靠，不受 SQL 腳本語法限制）
