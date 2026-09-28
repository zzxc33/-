# -*- coding: utf-8 -*-
"""
gen_seed_data.py — 为音乐推荐系统生成 500+ 歌曲 + 100+ 用户 + 5000+ 交互

运行方式：python gen_seed_data.py
需要：PyMySQL (pip install pymysql)

策略：
- 歌曲：覆盖 12 种风格，歌手约 200 位，每首歌都有封面和音频 URL
- 用户：100 位（user01 ~ user100），分 10 个偏好群组
- 交互：每用户 30~80 条，组内重叠度高（造 CF 邻居），组间低重叠
- play_count 用 lognormal 分布，点赞 25% 概率

运行后直接写入 music_recommendation 数据库
"""
import pymysql
import random
import math
from datetime import datetime, timedelta

random.seed(42)

# ========== 数据库连接 ==========
DB = pymysql.connect(host='localhost', port=3306,
                     user='root', password='123',
                     database='music_recommendation',
                     charset='utf8mb4', autocommit=True)

# ========== 1. 生成 430 首新歌曲（加上原 70 首共 500+） ==========

# 12 种风格 × 每风格约 40 首
GENRE_POOL = [
    ('流行',    60, ['周杰伦', '林俊杰', '陈奕迅', '邓紫棋', '张学友', '梁静茹', '李荣浩', '薛之谦', '毛不易', '赵雷', '五月天', '王力宏', '陶喆', '王力宏', '王菲', '刘若英', '蔡依林', '萧亚轩', 'S.H.E', '飞儿乐团', '南拳妈妈', '光良', '品冠', '阿杜', '林忆莲', '莫文蔚', '孙燕姿', '张韶涵', '周迅', '尚雯婕']),
    ('摇滚',    50, ['Beyond', '黑豹乐队', '唐朝乐队', '痛仰乐队', '新裤子', '反光镜', '脑浊', '五月天', '信乐团', '动力火车', '伍佰', '郑钧', '许巍', '汪峰', '朴树', '超载乐队', '面孔乐队', '陈慧琳', 'The Beatles', 'Queen', 'Eagles', 'OneRepublic', 'Imagine Dragons']),
    ('民谣',    50, ['赵雷', '马頔', '宋冬野', '好妹妹乐队', '陈粒', '麻油叶', '五条人', '陈鸿宇', '燕池', '谢春花', 'Jam', '阿城', '程璧', '阿肆', '何大河', '赵照', '梁晓雪', '曹方', '王宛之', '卢广仲']),
    ('古风',    45, ['许嵩', '周杰伦', '林俊杰', '董贞', '少司命', '河图', '音频怪物', 'HITA', '小曲儿', '银临', 'Aki阿杰', '叶洛洛', '双笙', '封茗囧菌', '洛天依', '叶非夜', '不才', '云横', 'Assen捷', '泥鳅Niko']),
    ('电子',    40, ['Martin Garrix', 'DJ Snake', 'The Chainsmokers', 'Calvin Harris', 'Kygo', 'Avicii', 'Alan Walker', 'Hardwell', 'Tiesto', 'KSHMR', 'Vicetone', 'Nicky Romero', 'Marshmello', 'Illenium', 'DJ L', '徐梦圆', 'Panta.Q', '周深', 'Tobu', 'Itro']),
    ('古典',    35, ['巴赫', '莫扎特', '贝多芬', '肖邦', '舒伯特', '柴可夫斯基', '拉赫玛尼诺夫', '德彪西', '李斯特', '帕格尼尼', '勃拉姆斯', '门德尔松', '海顿', '维瓦尔第', 'Handel', 'Dvorak', 'Mahler', 'Strauss', 'Ravel', 'Chopin']),
    ('轻音乐',  40, ['久石让', 'Yiruma', '李闰珉', 'Bandari', '班得瑞', 'Kenny G', 'George Winston', 'David Lanz', 'Enya', 'Secret Garden', '克莱德曼', '宋祖英', '吕方', '陈美', '范玮琪', '王瑞', 'Vangelis', 'Brian Eno', 'Max Richter', 'Ólafur Arnalds']),
    ('爵士',    35, ['Louis Armstrong', 'Miles Davis', 'John Coltrane', 'Ella Fitzgerald', 'Billie Holiday', 'Frank Sinatra', 'Tony Bennett', 'Diana Krall', 'Michael Bublé', 'Norah Jones', 'Lisa Ono', '小野丽莎', '王若琳', '方大同', '袁娅维', '李泉', '杜宣达', 'Mr. Miss', 'The Hot Sardines', 'Melody Gardot']),
    ('R&B',     35, ['Usher', 'R. Kelly', 'Ne-Yo', 'Beyoncé', 'Rihanna', 'Chris Brown', 'Bruno Mars', 'The Weeknd', 'Drake', 'Alicia Keys', 'John Legend', 'Trey Songz', 'Miguel', '方大同', '陶喆', '周杰伦', '李荣浩', '袁娅维', '关喆', '曹格']),
    ('嘻哈',    40, ['Eminem', 'Jay-Z', 'Kanye West', 'Drake', 'Travis Scott', 'Post Malone', 'Lil Wayne', 'Migos', 'Cardi B', 'Nicki Minaj', 'J. Cole', 'Kendrick Lamar', 'Tyler, The Creator', 'Gorillaz', '热狗', '张震岳', 'GAI周延', '艾热', '王以太', '刘聪', '雾都', '谢帝']),
    ('乡村',    30, ['Taylor Swift', 'Carrie Underwood', 'Kenny Chesney', 'Tim McGraw', 'Brad Paisley', 'Zac Brown Band', 'Lady A', 'Florida Georgia Line', 'Thomas Rhett', 'Kacey Musgraves', '老玉米', '陆小芝', '小娟和山谷里的居民', '林生祥', '交工乐队', '灭火器乐团', '旺福', '五月天', '草东没有派对', '告五人']),
    ('蓝调',    25, ['B.B. King', 'Eric Clapton', 'Stevie Ray Vaughan', 'Muddy Waters', 'John Lee Hooker', 'Bo Diddley', 'Chuck Berry', 'Buddy Guy', 'Etta James', 'Janis Joplin', 'The Rolling Stones', 'Led Zeppelin', 'Cream', 'Jeff Beck', 'Gary Moore', 'Joe Bonamassa', '苏芮', '齐秦', 'Beyond', '黑豹乐队']),
]

SONG_TITLE_TEMPLATES = {
    '流行': ['心跳', '思念', '晚风', '拥抱', '星光', '距离', '温柔', '勇敢', '倔强', '雨后', '晴天', '夜曲', '匆匆那年', '光年之外', '泡沫', '告白', '修炼爱情', '不为谁而作的歌', '江南', '曹操', '一千年以后', '小酒窝', '背对背拥抱', '那些你很冒险的梦', '可惜没如果', '大鱼', '起风了', '春风十里', '理想', '成都', '岁月神偷', '年少有为', '模特', '喜剧之王', '我', '丑八怪', '绅士', '刚刚好', '方圆几里', '其实'],
    '摇滚': ['海阔天空', '光辉岁月', '真的爱你', '灰色轨迹', '不再犹豫', '大地', '长城', 'AMANI', '无地自容', '黑梦', '梦回唐朝', '飞翔鸟', '姐姐', '私奔', '像梦一样自由', '蓝莲花', '天路', '怒放的生命', '飞得更高', '存在', '北京北京', '向阳花', '西湖', '安河桥', '生命中最美丽的一天', '再见杰克', '公路之歌', '愿爱无忧', '为你唱首歌', '时代在召唤'],
    '民谣': ['南山南', '董小姐', '安和桥', '斑马斑马', '成都', '奇妙能力歌', '历历万乡', '小幸运', '说散就散', '消愁', '像我这样的人', '我曾', '平凡的一天', '五月的你', '三十岁的女人', '理想三旬', '夜空中最亮的星', '一万次悲伤', '再见,我的爱人', '走马', '七月上', '差三岁', '遇见', '南方姑娘', '北方的女王', '莉莉安', '奇妙的朋友', '我们的歌', '写给黄淮', '关于郑州的记忆'],
    '古风': ['青花瓷', '东风破', '烟花易冷', '清明雨上', '庐州月', '赤伶', '千百度', '河山大好', '断桥残雪', '燕归巢', '梧桐灯', '绝代风华', '山水之间', '七夕', '有桃花', '弹指一挥间', '弹指歌', '故人叹', '弱水三千', '锦鲤抄', '腐草为萤', '棠梨煎雪', '不老梦', '银汉昭昭', '牵丝戏', '故梦', '东风志', '明月天涯', '倾尽天下', '为龙'],
    '电子': ['Closer', 'Animals', 'Wake Me Up', 'Faded', 'Alone', 'Titanium', 'Lean On', 'Let Me Love You', 'Something Just Like This', 'Roses', 'Happier', 'Happier Than Ever', 'Blinding Lights', 'Starboy', 'Shape of You', 'Castle on the Hill', 'Perfect', 'Despacito', 'Señorita', 'Senorita', 'Wolves', 'In the Name of Love', 'Don\'t You Worry Child', 'Save the World', 'Levels', 'Sunset Lover', 'Lone Ranger', 'Horizon', 'Monody', 'Heroes'],
    '古典': ['Concerto for 2 Violins', 'Canon in D', 'Moonlight Sonata', 'Für Elise', 'Clair de Lune', 'Swan Lake', 'The Nutcracker', 'Eine kleine Nachtmusik', 'Piano Sonata No. 8', 'Symphony No. 5', 'Symphony No. 9', 'The Four Seasons', 'Largo', 'Adagio for Strings', 'La Valse d\'Amélie', 'Gymnopédie No. 1', 'Nocturne Op. 9 No. 2', 'Prelude in C', 'Rondo alla Turca', 'Eine kleine Nachtmusik', 'The Well-Tempered Clavier', 'Goldberg Variations', 'Six Partitas', 'Italian Concerto', 'English Suite', 'French Suite', 'Cello Suites', 'Partita for Violin', 'Chaconne', 'Chromatic Fantasy'],
    '轻音乐': ['River Flows in You', 'Spring Snow', 'Kiss the Rain', 'Maybe', 'One Day', 'Love Me', 'Merry Christmas Mr. Lawrence', 'The Truth That You Leave', 'Luv Letter', 'Flower Dance', 'Refrain', 'Annie\'s Wonderland', 'Childhood Memory', 'Sunny Day', 'Snowdrops', 'Morning Glory', 'Indian Summer', 'The Best Friends', 'Good Morning', 'One More Chance', '雨的印记', '梦中的婚礼', '秋日私语', '献给爱丽丝', '水边的阿狄丽娜', '童年', '神秘园之歌', 'you and me', '夏影', '月半小夜曲'],
    '爵士': ['Take Five', 'So What', 'My Favorite Things', 'Feeling Good', 'At Last', 'I Got You Under My Skin', 'The Lady is a Tramp', 'It Don\'t Mean a Thing', 'Fly Me to the Moon', 'I Only Have Eyes for You', 'When I Fall in Love', 'Cheek to Cheek', 'Night and Day', 'The Way You Look Tonight', 'L-O-V-E', 'What a Wonderful World', 'Sittin\' On the Dock of the Bay', 'Cry Me a River', 'At This Moment', 'Unforgettable', 'Smile', 'Over the Rainbow', 'La Vie en Rose', 'Sway', 'Quizás, Quizás, Quizás', 'Besame Mucho', 'Corcovado', 'Girl from Ipanema', 'Wave', 'Black Orpheus'],
    'R&B': ['Climax', 'Let Me Love You', 'Yeah!', 'U Remind Me', 'Confessions Part II', 'The Way You Move', 'Ignition', 'Burn', 'Superstar', 'Be Without You', 'We Belong Together', 'Crazy in Love', 'Single Ladies', 'Irreplaceable', 'Halo', 'Rude Boy', 'What\'s My Name?', 'Diamonds', 'Love the Way You Lie', 'The Monster', 'Uptown Funk', '24K Magic', 'That\'s What I Like', 'Marry You', 'Just the Way You Are', 'Grenade', 'Treasure', 'Locked Out of Heaven', 'Versace on the Floor', 'When I Was Your Man'],
    '嘻哈': ['Lose Yourself', 'Stan', 'Rap God', 'Not Afraid', 'Love the Way You Lie', '99 Problems', 'Empire State of Mind', 'Run This Town', 'Stronger', 'Gold Digger', 'Jesus Walks', 'All of the Lights', 'N***** in Paris', 'Power', 'Started from the Bottom', 'Hotline Bling', 'One Dance', 'God\'s Plan', 'In My Feelings', 'Sicko Mode', 'Stargazing', 'Goosebumps', 'Look Alive', 'Psycho', 'Rockstar', 'Blase', 'Congratulations', 'HUMBLE.', 'DNA', 'ELEMENT.'],
    '乡村': ['Fearless', 'Love Story', 'You Belong With Me', 'White Horse', 'Fifteen', 'Mine', 'Back to December', 'Mean', 'Dear John', 'Enchanted', 'Long Live', 'Haunted', 'Red', '22', 'We Are Never Ever Getting Back Together', 'Begin Again', 'State of Grace', 'All Too Well', 'Shake It Off', 'Blank Space', 'Wildest Dreams', 'Out of the Woods', 'Clean', 'Style', 'New Romantics', 'Delicate', 'Look What You Made Me Do', 'End Game', 'Gorgeous', 'Getaway Car'],
    '蓝调': ['The Thrill Is Gone', 'Crossroads', 'Voodoo Child', 'Red House', 'Pride and Joy', 'Texas Flood', 'The House Is Rockin\'', 'Hoochie Coochie Man', 'Mannish Boy', 'Hurt', 'At Last', 'I Can\'t Quit You Baby', 'Killing Floor', 'When the Levee Breaks', 'Led Zeppelin IV', 'Whole Lotta Love', 'Black Dog', 'Kashmir', 'Stairway to Heaven', 'Hotel California', 'Life in the Fast Lane', 'Take It Easy', 'Desperado', 'New Kid in Town', 'Lyin\' Eyes', 'B.B. King Blues', 'Stormy Monday', 'Sweet Home Chicago', 'Born Under a Bad Sign', 'La Grange'],
}

DURATION_POOL = ['03:15', '03:30', '03:45', '04:00', '04:15', '04:30', '04:45', '05:00', '05:15', '05:30']

# 先读取现有歌曲，避免重复插入
with DB.cursor() as cur:
    cur.execute("SELECT title, artist FROM songs")
    existing_pairs = set((t or '', a or '') for t, a in cur.fetchall())
    cur.execute("SELECT COUNT(*) FROM songs")
    existing_count = cur.fetchone()[0]

print(f"📊 现有歌曲 {existing_count} 首")

# 生成新歌曲
new_songs = []
for genre, count, artists in GENRE_POOL:
    titles = SONG_TITLE_TEMPLATES.get(genre, [f'{genre}之{i}' for i in range(count)])
    # 扩展标题列表（重复使用模板）
    while len(titles) < count:
        titles = titles + [f'{t} · {i}' for i, t in enumerate(titles)]

    for i in range(count):
        title = titles[i] if i < len(titles) else f'{genre}精选{i}'
        artist = random.choice(artists)
        pair = (title, artist)
        if pair in existing_pairs:
            continue
        existing_pairs.add(pair)

        album = f'{artist}精选集 Vol.{random.randint(1, 5)}'
        duration = random.choice(DURATION_POOL)
        play_count = max(100, int(random.lognormvariate(6, 1.5)))
        like_count = max(10, int(play_count * random.uniform(0.05, 0.25)))
        cover_url = f'https://picsum.photos/seed/song{abs(hash(title + artist)) % 10000}/300/300'
        audio_url = f'/audio/sample{(abs(hash(title)) % 20) + 1}.mp3'
        description = f'{genre}风格经典作品，来自{artist}'

        new_songs.append((title, artist, album, cover_url, audio_url, genre, duration,
                          play_count, like_count, description, datetime.now() - timedelta(days=random.randint(0, 730))))

# 批量插入
with DB.cursor() as cur:
    cur.executemany("""
        INSERT INTO songs (title, artist, album, cover_url, audio_url, genre, duration,
                           play_count, like_count, description, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """, new_songs)

with DB.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM songs")
    total_songs = cur.fetchone()[0]
print(f"🎵 新增 {len(new_songs)} 首歌曲，累计 {total_songs} 首")

# ========== 2. 造 100 位用户 ==========
BCRYPT_PW = '$2a$10$NBSNHxipkBcpYp9tJcnU9OHWNEbIcgSE0pz8Ba2lsIjlcWQYBJShu'

# 10 个偏好群组
PREF_GROUPS = [
    {'name': '流行少年',  'primary': '流行', 'secondary': '电子', 'size': 10},
    {'name': '摇滚老炮',  'primary': '摇滚', 'secondary': '蓝调', 'size': 10},
    {'name': '民谣文青',  'primary': '民谣', 'secondary': '乡村', 'size': 10},
    {'name': '古风爱好者', 'primary': '古风', 'secondary': '轻音乐', 'size': 10},
    {'name': '电子DJ',    'primary': '电子', 'secondary': '嘻哈', 'size': 10},
    {'name': '古典控',    'primary': '古典', 'secondary': '轻音乐', 'size': 10},
    {'name': '爵士迷',    'primary': '爵士', 'secondary': '蓝调', 'size': 10},
    {'name': 'R&B灵魂',   'primary': 'R&B', 'secondary': '流行', 'size': 10},
    {'name': '嘻哈党',    'primary': '嘻哈', 'secondary': '电子', 'size': 10},
    {'name': '杂食动物',  'primary': '流行', 'secondary': '民谣', 'size': 10},
]

# 读取现有用户
with DB.cursor() as cur:
    cur.execute("SELECT id, username FROM users ORDER BY id")
    existing_users = cur.fetchall()

existing_ids = set(uid for uid, _ in existing_users)
next_id = max(existing_ids) + 1 if existing_ids else 1

new_users = []
for gi, g in enumerate(PREF_GROUPS):
    for i in range(g['size']):
        uname = f"user{next_id:02d}"
        new_users.append((uname, BCRYPT_PW, f"{uname}@music.edu.cn",
                         f"{uname}（偏好{g['primary']}）", 'ROLE_USER', g['primary']))
        next_id += 1

# 插入用户
with DB.cursor() as cur:
    cur.executemany("""
        INSERT INTO users (username, password, email, nickname, role, favorite_genre, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, NOW(), NOW())
    """, new_users)

with DB.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM users")
    total_users = cur.fetchone()[0]
print(f"👥 新增 {len(new_users)} 用户，累计 {total_users} 位")

# ========== 3. 造交互数据 ==========

# 先清空旧交互，避免冲突
with DB.cursor() as cur:
    cur.execute("DELETE FROM user_song_interactions")
    cur.execute("UPDATE songs SET play_count = GREATEST(play_count, 100)")  # 保持基础播放数

# 读取歌曲
with DB.cursor() as cur:
    cur.execute("SELECT id, genre FROM songs ORDER BY id")
    songs_all = cur.fetchall()

all_song_ids = [sid for sid, _ in songs_all]
genre_to_songs = {}
for sid, g in songs_all:
    genre_to_songs.setdefault(g, []).append(sid)

# 读取所有用户
with DB.cursor() as cur:
    cur.execute("SELECT id FROM users ORDER BY id")
    all_users = [uid for (uid,) in cur.fetchall()]

# 为每个用户分配群组
user_group = {}  # uid → group_index
uid = 0
for gi, g in enumerate(PREF_GROUPS):
    for _ in range(g['size']):
        if uid < len(all_users):
            user_group[all_users[uid]] = gi
            uid += 1

# 现有 admin/user 也分配群组
for existing_uid, _ in existing_users:
    if existing_uid not in user_group and existing_uid != 3:  # 3=admin
        gi = (existing_uid - 1) % len(PREF_GROUPS)
        user_group[existing_uid] = gi

print(f"🎧 交互生成中... 共 {len(user_group)} 位活跃用户")

# 批量造交互
base_time = datetime.now()
all_interactions = []
song_play_boost = {}

for uid, gi in user_group.items():
    g = PREF_GROUPS[gi]
    primary_songs = genre_to_songs.get(g['primary'], [])
    secondary_songs = genre_to_songs.get(g['secondary'], [])
    pref_pool = primary_songs + random.sample(secondary_songs, min(len(secondary_songs), 15))
    pref_set = set(pref_pool)

    # 每用户 30~80 条
    n = random.randint(30, 80)
    n_pref = int(n * 0.75)  # 75% 偏好歌
    n_explore = n - n_pref

    # 随机取样
    chosen_pref = random.sample(pref_pool, min(n_pref, len(pref_pool)))
    others = [s for s in all_song_ids if s not in pref_set]
    chosen_explore = random.sample(others, min(n_explore, len(others)))

    for sid in chosen_pref + chosen_explore:
        is_pref = sid in pref_set
        pc = max(1, int(random.lognormvariate(0.8 if is_pref else 0.3, 0.6)))
        like_prob = 0.35 if is_pref else 0.15
        liked = 1 if random.random() < like_prob else 0
        days_ago = random.randint(0, 60)
        last_played = base_time - timedelta(days=days_ago, hours=random.randint(0, 23))
        liked_at = (last_played - timedelta(days=random.randint(0, 3))) if liked else None

        all_interactions.append((uid, sid, pc, liked, last_played, liked_at, last_played))
        song_play_boost[sid] = song_play_boost.get(sid, 0) + pc

# admin 造点数据
admin_id = 3
for _ in range(25):
    sid = random.choice(all_song_ids)
    pc = max(1, int(random.lognormvariate(0.5, 0.5)))
    liked = 1 if random.random() < 0.2 else 0
    days_ago = random.randint(0, 45)
    last_played = base_time - timedelta(days=days_ago)
    all_interactions.append((admin_id, sid, pc, liked, last_played, None, last_played))
    song_play_boost[sid] = song_play_boost.get(sid, 0) + pc

# 去重：同一 (uid, sid) 只保留一条（取 play_count 更大的）
print(f"  去重前 {len(all_interactions)} 条...")
interaction_map = {}  # (uid, sid) → row
for row in all_interactions:
    key = (row[0], row[1])
    if key not in interaction_map or row[2] > interaction_map[key][2]:
        interaction_map[key] = row
all_interactions = list(interaction_map.values())
print(f"  去重后 {len(all_interactions)} 条")

# 批量写入
with DB.cursor() as cur:
    cur.executemany("""
        INSERT INTO user_song_interactions
        (user_id, song_id, play_count, is_liked, last_played_at, liked_at, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """, all_interactions)

# 更新歌曲聚合数据
with DB.cursor() as cur:
    for sid, pc in song_play_boost.items():
        cur.execute("UPDATE songs SET play_count = GREATEST(play_count, %s) WHERE id = %s", (pc, sid))

with DB.cursor() as cur:
    cur.execute("SELECT song_id, COUNT(*) FROM user_song_interactions WHERE is_liked=1 GROUP BY song_id")
    for sid, lc in cur.fetchall():
        cur.execute("UPDATE songs SET like_count = GREATEST(like_count, %s) WHERE id = %s", (lc, sid))

# ========== 4. 最终统计 ==========
with DB.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM users")
    nu = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM songs")
    ns = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM user_song_interactions")
    ni = cur.fetchone()[0]
    cur.execute("SELECT COUNT(DISTINCT user_id) FROM user_song_interactions")
    nua = cur.fetchone()[0]
    cur.execute("SELECT COUNT(DISTINCT song_id) FROM user_song_interactions")
    nsa = cur.fetchone()[0]
    cur.execute("SELECT user_id, COUNT(*) FROM user_song_interactions GROUP BY user_id")
    per_user = cur.fetchall()
    cur.execute("SELECT genre, COUNT(*) FROM songs GROUP BY genre")
    genre_dist = cur.fetchall()

print(f"\n{'='*45}")
print(f"  🎊 数据生成完成！")
print(f"{'='*45}")
print(f"  用户总数  : {nu}   (活跃 {nua})")
print(f"  歌曲总数  : {ns}   (有交互 {nsa})")
print(f"  交互总数  : {ni}")
print(f"  每用户交互: min={min(c for _,c in per_user)} max={max(c for _,c in per_user)} avg={ni/nua:.1f}")
print(f"\n  歌曲风格分布:")
for g, cnt in genre_dist:
    print(f"    {g}: {cnt}")
print(f"\n  用户偏好群组:")
for gi, g in enumerate(PREF_GROUPS):
    members = [uid for uid, gidx in user_group.items() if gidx == gi]
    print(f"    [{gi}] {g['name']:8s} → {len(members)} 用户")

DB.close()
print(f"\n✅ 全部完成！重启 Spring Boot 即可看到新数据")
