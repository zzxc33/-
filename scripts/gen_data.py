# -*- coding: utf-8 -*-
"""
gen_data.py — 为音乐推荐系统生成真实模拟用户交互数据

策略：
- 保留现有 4 用户（admin + 3 用户）
- 新增 46 用户（user05 ~ user50），共 50 用户
- 每 6-8 用户一组，有共同偏好 genre/artist → 造协同过滤相似性
- 组内重叠 40-60% 歌曲，组间低重叠
- 每个用户 ~30 条交互，总计 ~1500 条
- play_count 用 lognormal 分布，is_liked 约 25%

运行：python gen_data.py  （需要 PyMySQL）
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

# ========== 1. 读取现有歌曲 ==========
with DB.cursor() as cur:
    cur.execute("SELECT id, title, artist, album, genre, play_count FROM songs ORDER BY id")
    songs = cur.fetchall()

SONGS = {}  # id → dict
GENRE_SONGS = {}  # genre → [song_ids]
ARTIST_SONGS = {}  # artist → [song_ids]
for sid, title, artist, album, genre, pc in songs:
    SONGS[sid] = {'id': sid, 'title': title, 'artist': artist, 'album': album, 'genre': genre, 'play_count': pc or 0}
    GENRE_SONGS.setdefault(genre, []).append(sid)
    ARTIST_SONGS.setdefault(artist, []).append(sid)

ALL_SONG_IDS = list(SONGS.keys())
GENRES = list(GENRE_SONGS.keys())
print(f"✅ 已加载 {len(SONGS)} 首歌曲, {len(GENRES)} 种风格")
for g in GENRES:
    print(f"   {g}: {len(GENRE_SONGS[g])} 首")

# ========== 2. 定义 7 个偏好群组（每群 6-8 用户） ==========
# 每个群组偏好 2-3 种 genre + 若干 artist
GROUPS = [
    # 群组 A: 摇滚 + 民谣
    {'name': 'RockFolk', 'primary': [g for g in GENRES if '摇滚' in g],
     'secondary': [g for g in GENRES if '民谣' in g],
     'size': 7},
    # 群组 B: 电子 + R&B
    {'name': 'Electro', 'primary': [g for g in GENRES if '电子' in g],
     'secondary': [g for g in GENRES if 'R&B' in g or '蓝调' in g],
     'size': 7},
    # 群组 C: 流行 + 爵士
    {'name': 'PopJazz', 'primary': [g for g in GENRES if '流行' in g],
     'secondary': [g for g in GENRES if '爵士' in g],
     'size': 8},
    # 群组 D: 古典 + 影视原声
    {'name': 'Classical', 'primary': [g for g in GENRES if '古典' in g],
     'secondary': [g for g in GENRES if '原声' in g or '影视' in g],
     'size': 7},
    # 群组 E: 民谣 + 乡村
    {'name': 'FolkCountry', 'primary': [g for g in GENRES if '民谣' in g],
     'secondary': [g for g in GENRES if '乡村' in g],
     'size': 7},
    # 群组 F: 摇滚 + 金属
    {'name': 'RockMetal', 'primary': [g for g in GENRES if '摇滚' in g],
     'secondary': [g for g in GENRES if '金属' in g or '朋克' in g],
     'size': 8},
    # 群组 G: 流行 + 电子（年轻向）
    {'name': 'PopElectro', 'primary': [g for g in GENRES if '流行' in g],
     'secondary': [g for g in GENRES if '电子' in g],
     'size': 6},
]

# 过滤掉空群组（有些 genre 可能不存在）
def expand_group(g):
    """返回这个群组对应的所有歌曲 ID"""
    gids = []
    for gn in g['primary'] + g['secondary']:
        gids.extend(GENRE_SONGS.get(gn, []))
    return list(set(gids))

for g in GROUPS:
    g['song_ids'] = expand_group(g)
    print(f"   群组 {g['name']}: {len(g['song_ids'])} 首候选, {g['size']} 用户")

# 过滤掉候选歌太少的群组
GROUPS = [g for g in GROUPS if len(g['song_ids']) >= 10]

# ========== 3. 造用户 ==========
with DB.cursor() as cur:
    cur.execute("SELECT id, username FROM users ORDER BY id")
    existing_users = cur.fetchall()

existing_ids = set(uid for uid, _ in existing_users)
next_id = max(existing_ids) + 1 if existing_ids else 1

# BCrypt hash of "123456" (using user3/admin's hash as template, same password)
BCRYPT_PW = '$2a$10$NBSNHxipkBcpYp9tJcnU9OHWNEbIcgSE0pz8Ba2lsIjlcWQYBJShu'

new_users = []  # (username, password, email, nickname, role, favorite_genre)
for gi, g in enumerate(GROUPS):
    primary_g = g['primary'][0] if g['primary'] else g['secondary'][0]
    for i in range(g['size']):
        uname = f"user{next_id:02d}"
        new_users.append((uname, BCRYPT_PW, f"{uname}@music.edu.cn",
                         f"{uname}（偏好{primary_g}）", 'ROLE_USER', primary_g))
        next_id += 1

print(f"\n📝 准备新增 {len(new_users)} 用户")

# 插入用户
with DB.cursor() as cur:
    for u in new_users:
        cur.execute("""
            INSERT INTO users (username, password, email, nickname, role, favorite_genre, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, NOW(), NOW())
        """, u)

# 更新全局用户 ID 表
with DB.cursor() as cur:
    cur.execute("SELECT id, username FROM users ORDER BY id")
    ALL_USERS = cur.fetchall()  # [(id, username), ...]

# 建立群组 → 成员 ID 的映射
group_user_ids = []  # [[uid1, uid2, ...], [uid3, ...], ...]
uid_to_group = {}  # uid → group index

uid_idx = 0
for g in GROUPS:
    members = []
    for _ in range(g['size']):
        if uid_idx < len(ALL_USERS):
            members.append(ALL_USERS[uid_idx][0])
            uid_to_group[ALL_USERS[uid_idx][0]] = len(group_user_ids)
            uid_idx += 1
    group_user_ids.append(members)

# 把现有 4 个用户分到不同群组（为了不冲突，追加到各个组）
for i, (uid, uname) in enumerate(existing_users):
    if uid == 3:  # admin 不分
        continue
    gi = i % len(GROUPS)
    group_user_ids[gi].append(uid)
    uid_to_group[uid] = gi

print(f"👥 用户总数: {len(ALL_USERS)}")
for gi, (g, members) in enumerate(zip(GROUPS, group_user_ids)):
    print(f"   群组{gi} {g['name']}: {len(members)} 用户 (ids={members[:3]}...)")

# ========== 4. 造交互 ==========
# 清空旧交互 + 重置 play_count
with DB.cursor() as cur:
    cur.execute("DELETE FROM user_song_interactions")
    cur.execute("UPDATE songs SET play_count = 0, like_count = 0")

# 造时间：过去 30 天随机
base_time = datetime.now()

inserted = 0
song_play_boost = {}  # song_id → 被播放次数，用于更新 songs.play_count

def gen_interactions_for_user(uid, group_song_ids, all_song_ids, interactions_range=(18, 36)):
    """为单个用户造交互"""
    n = random.randint(*interactions_range)
    
    # 80% 来自组内偏好歌，20% 随机探索
    n_prefer = int(n * 0.80)
    n_explore = n - n_prefer
    
    prefer_songs = random.sample(group_song_ids, min(n_prefer, len(group_song_ids)))
    
    # 排除掉偏好歌，从其余里随机选探索歌
    other_songs = [s for s in all_song_ids if s not in set(group_song_ids)]
    if not other_songs:
        other_songs = all_song_ids
    explore_songs = random.sample(other_songs, min(n_explore, len(other_songs)))
    
    result = []
    for sid in prefer_songs + explore_songs:
        # play_count: lognormal，均值约 3-5
        pc = max(1, int(random.lognormvariate(0.5, 0.7)))
        # is_liked: 25% 概率，偏好歌更容易被点赞（35%）
        is_prefer = sid in set(group_song_ids)
        like_prob = 0.35 if is_prefer else 0.15
        liked = 1 if random.random() < like_prob else 0
        # 时间：过去 30 天
        days_ago = random.randint(0, 30)
        last_played = base_time - timedelta(days=days_ago, hours=random.randint(0,23))
        liked_at = (last_played - timedelta(days=random.randint(0,5))) if liked else None
        
        result.append((uid, sid, pc, liked, last_played, liked_at, last_played))
    
    return result

# 先为每个用户造交互
all_interactions = []
for uid, uname in ALL_USERS:
    if uid == 3:  # admin 跳过
        continue
    gi = uid_to_group.get(uid, 0)
    group_songs = GROUPS[gi]['song_ids']
    its = gen_interactions_for_user(uid, group_songs, ALL_SONG_IDS)
    all_interactions.extend(its)
    inserted += len(its)
    # 累加 play_count boost
    for _, sid, pc, *_ in its:
        song_play_boost[sid] = song_play_boost.get(sid, 0) + pc

# 为 admin 也造少量数据（跨群随机，冷启动场景）
for i in range(10):
    sid = random.choice(ALL_SONG_IDS)
    all_interactions.append((3, sid, max(1, int(random.lognormvariate(0.3, 0.5))), 0,
                              base_time - timedelta(days=random.randint(0,20)), None,
                              base_time - timedelta(days=random.randint(0,20))))

print(f"\n🎵 准备写入 {len(all_interactions)} 条交互")

# 批量插入
with DB.cursor() as cur:
    for row in all_interactions:
        cur.execute("""
            INSERT INTO user_song_interactions
            (user_id, song_id, play_count, is_liked, last_played_at, liked_at, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, row)

# 更新 songs 表聚合数据
with DB.cursor() as cur:
    for sid, pc in song_play_boost.items():
        cur.execute("UPDATE songs SET play_count = GREATEST(play_count, %s) WHERE id = %s", (pc, sid))

# 统计点赞数
with DB.cursor() as cur:
    cur.execute("SELECT song_id, COUNT(*) FROM user_song_interactions WHERE is_liked=1 GROUP BY song_id")
    for sid, lc in cur.fetchall():
        cur.execute("UPDATE songs SET like_count = GREATEST(like_count, %s) WHERE id = %s", (lc, sid))

# ========== 5. 最终统计 ==========
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
    cur.execute("SELECT user_id, COUNT(*) FROM user_song_interactions GROUP BY user_id ORDER BY user_id")
    per_user = cur.fetchall()

print(f"\n========== 数据生成完成 ==========")
print(f"用户总数: {nu} (活跃: {nua})")
print(f"歌曲总数: {ns} (有交互: {nsa})")
print(f"交互总数: {ni}")
print(f"每用户交互数: min={min(c for _,c in per_user)}, max={max(c for _,c in per_user)}, avg={ni/nua:.1f}")
print(f"\n各用户交互分布:")
for uid, cnt in per_user:
    gn = GROUPS[uid_to_group.get(uid, 0)]['name'] if uid_to_group.get(uid) else 'admin'
    print(f"  user_{uid:02d}: {cnt:3d} 条  [{gn}]")

DB.close()
print("\n✅ 数据库已更新！")
