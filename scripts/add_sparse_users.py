# -*- coding: utf-8 -*-
"""造 5 个稀疏新用户（3-5 条交互）"""
import pymysql, random, sys

DB = pymysql.connect(host='localhost', port=3306, user='root', password='123',
                     database='music_recommendation', charset='utf8mb4', autocommit=True)

with DB.cursor() as cur:
    cur.execute("SELECT MAX(id) FROM users")
    nxt = cur.fetchone()[0] + 1

    cur.execute("SELECT id FROM songs ORDER BY RAND()")
    all_songs = [r[0] for r in cur.fetchall()]

    # BCrypt hash of "123456"
    PW = '$2a$10$NBSNHxipkBcpYp9tJcnU9OHWNEbIcgSE0pz8Ba2lsIjlcWQYBJShu'

    for i in range(5):
        cur.execute("INSERT INTO users (username, password, email, nickname, role, favorite_genre, created_at, updated_at) VALUES (%s, %s, %s, %s, 'ROLE_USER', '流行', NOW(), NOW())",
                    (f'new_{nxt+i:02d}', PW, f'new_{nxt+i:02d}@new.com', f'new_{nxt+i:02d}'))
        cur.execute("SELECT LAST_INSERT_ID()")
        real_uid = cur.fetchone()[0]
        picks = random.sample(all_songs, random.randint(3, 5))
        for sid in picks:
            cur.execute("INSERT INTO user_song_interactions (user_id, song_id, play_count, is_liked, last_played_at, created_at) VALUES (%s, %s, %s, %s, NOW(), NOW())",
                       (real_uid, sid, random.randint(1, 3), random.choice([0, 0, 1])))

DB.close()

# 统计
DB2 = pymysql.connect(host='localhost', port=3306, user='root', password='123',
                      database='music_recommendation', charset='utf8mb4', autocommit=True)
with DB2.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM users")
    nu = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM user_song_interactions")
    ni = cur.fetchone()[0]
    cur.execute("""
        SELECT u.id, COUNT(i.id) as int_cnt
        FROM users u LEFT JOIN user_song_interactions i ON i.user_id = u.id
        GROUP BY u.id ORDER BY int_cnt ASC
    """)
    print(f"✅ 已造 5 个稀疏新用户（共 {nu} 用户, {ni} 交互）")
    print("按交互数排序的用户列表（前 10）:")
    for uid, cnt in cur.fetchall()[:10]:
        tag = "⚠️ 稀疏" if cnt <= 5 else ""
        print(f"  uid={uid}: {cnt} 条 {tag}")
DB2.close()
