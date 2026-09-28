# -*- coding: utf-8 -*-
"""gen_interactions.py — 只造交互数据（歌曲/用户已存在时用这个）"""
import pymysql
import random
from datetime import datetime, timedelta

random.seed(42)

DB = pymysql.connect(host='localhost', port=3306,
                     user='root', password='123',
                     database='music_recommendation',
                     charset='utf8mb4', autocommit=True)

# 清空旧交互
with DB.cursor() as cur:
    cur.execute("DELETE FROM user_song_interactions")
print("🧹 已清空旧交互")

# 读取歌曲
with DB.cursor() as cur:
    cur.execute("SELECT id, genre FROM songs ORDER BY id")
    songs_all = cur.fetchall()

all_song_ids = [sid for sid, _ in songs_all]
genre_to_songs = {}
for sid, g in songs_all:
    genre_to_songs.setdefault(g, []).append(sid)
print(f"🎵 已加载 {len(all_song_ids)} 首歌, {len(genre_to_songs)} 种风格")

# 读取用户
with DB.cursor() as cur:
    cur.execute("SELECT id FROM users ORDER BY id")
    all_users = [uid for (uid,) in cur.fetchall()]
print(f"👥 已加载 {len(all_users)} 位用户")

# 10 个偏好群组
PREF_GROUPS = [
    {'name': '流行少年',  'primary': '流行', 'secondary': '电子'},
    {'name': '摇滚老炮',  'primary': '摇滚', 'secondary': '蓝调'},
    {'name': '民谣文青',  'primary': '民谣', 'secondary': '乡村'},
    {'name': '古风爱好者', 'primary': '古风', 'secondary': '轻音乐'},
    {'name': '电子DJ',    'primary': '电子', 'secondary': '嘻哈'},
    {'name': '古典控',    'primary': '古典', 'secondary': '轻音乐'},
    {'name': '爵士迷',    'primary': '爵士', 'secondary': '蓝调'},
    {'name': 'R&B灵魂',   'primary': 'R&B', 'secondary': '流行'},
    {'name': '嘻哈党',    'primary': '嘻哈', 'secondary': '电子'},
    {'name': '杂食动物',  'primary': '流行', 'secondary': '民谣'},
]

# 给用户分配群组（均匀分配）
user_group = {}
for i, uid in enumerate(all_users):
    user_group[uid] = i % len(PREF_GROUPS)

# 造交互
base_time = datetime.now()
all_interactions = []
song_play_boost = {}

for uid, gi in user_group.items():
    g = PREF_GROUPS[gi]
    primary_songs = genre_to_songs.get(g['primary'], [])
    secondary_songs = genre_to_songs.get(g['secondary'], [])
    pref_pool = primary_songs + random.sample(secondary_songs, min(len(secondary_songs), 15))
    pref_set = set(pref_pool)

    n = random.randint(30, 80)
    n_pref = int(n * 0.75)
    n_explore = n - n_pref

    chosen_pref = random.sample(pref_pool, min(n_pref, len(pref_pool)))
    others = [s for s in all_song_ids if s not in pref_set]
    chosen_explore = random.sample(others, min(n_explore, len(others)))

    for sid in chosen_pref + chosen_explore:
        is_pref = sid in pref_set
        pc = max(1, int(random.lognormvariate(0.8 if is_pref else 0.3, 0.6)))
        liked = 1 if random.random() < (0.35 if is_pref else 0.15) else 0
        days_ago = random.randint(0, 60)
        last_played = base_time - timedelta(days=days_ago, hours=random.randint(0, 23))
        liked_at = (last_played - timedelta(days=random.randint(0, 3))) if liked else None

        all_interactions.append((uid, sid, pc, liked, last_played, liked_at, last_played))
        song_play_boost[sid] = song_play_boost.get(sid, 0) + pc

# admin 造点跨群数据
for _ in range(25):
    sid = random.choice(all_song_ids)
    pc = max(1, int(random.lognormvariate(0.5, 0.5)))
    liked = 1 if random.random() < 0.2 else 0
    days_ago = random.randint(0, 45)
    last_played = base_time - timedelta(days=days_ago)
    all_interactions.append((3, sid, pc, liked, last_played, None, last_played))
    song_play_boost[sid] = song_play_boost.get(sid, 0) + pc

# 去重
interaction_map = {}
for row in all_interactions:
    key = (row[0], row[1])
    if key not in interaction_map or row[2] > interaction_map[key][2]:
        interaction_map[key] = row
all_interactions = list(interaction_map.values())

print(f"🎧 准备写入 {len(all_interactions)} 条交互（去重后）")

# 分批写入（避免单次 SQL 太长）
batch_size = 1000
with DB.cursor() as cur:
    for i in range(0, len(all_interactions), batch_size):
        batch = all_interactions[i:i + batch_size]
        cur.executemany("""
            INSERT INTO user_song_interactions
            (user_id, song_id, play_count, is_liked, last_played_at, liked_at, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, batch)
        print(f"   写入 {min(i + batch_size, len(all_interactions))}/{len(all_interactions)}")

# 更新歌曲聚合
with DB.cursor() as cur:
    for sid, pc in song_play_boost.items():
        cur.execute("UPDATE songs SET play_count = GREATEST(play_count, %s) WHERE id = %s", (pc, sid))
    cur.execute("SELECT song_id, COUNT(*) FROM user_song_interactions WHERE is_liked=1 GROUP BY song_id")
    for sid, lc in cur.fetchall():
        cur.execute("UPDATE songs SET like_count = GREATEST(like_count, %s) WHERE id = %s", (lc, sid))

# 最终统计
with DB.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM user_song_interactions")
    ni = cur.fetchone()[0]
    cur.execute("SELECT COUNT(DISTINCT user_id) FROM user_song_interactions")
    nua = cur.fetchone()[0]
    cur.execute("SELECT COUNT(DISTINCT song_id) FROM user_song_interactions")
    nsa = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM songs")
    ns = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM users")
    nu = cur.fetchone()[0]
    cur.execute("SELECT user_id, COUNT(*) FROM user_song_interactions GROUP BY user_id")
    per_user = cur.fetchall()

print(f"\n{'='*45}")
print(f"  🎊 交互生成完成！")
print(f"{'='*45}")
print(f"  用户  : {nu} (活跃 {nua})")
print(f"  歌曲  : {ns} (有交互 {nsa})")
print(f"  交互  : {ni}")
print(f"  每用户: min={min(c for _,c in per_user)} max={max(c for _,c in per_user)} avg={ni/nua:.1f}")

DB.close()
print("\n✅ 完成！")
