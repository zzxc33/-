package com.music.recommendation.config;

import com.music.recommendation.entity.Playlist;
import com.music.recommendation.entity.PlaylistSong;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.PlaylistRepository;
import com.music.recommendation.repository.PlaylistSongRepository;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public DataInitializer(UserRepository userRepository, SongRepository songRepository,
                           PlaylistRepository playlistRepository, PlaylistSongRepository playlistSongRepository,
                           PasswordEncoder passwordEncoder, TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.playlistRepository = playlistRepository;
        this.playlistSongRepository = playlistSongRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(String... args) {
        // 1. 数据库迁移：为 users 表补充 role 列（独立事务，避免回滚影响）
        migrateRoleColumn();

        // 2. 为已有用户补充默认角色
        userRepository.findAll().forEach(u -> {
            if (u.getRole() == null) {
                u.setRole("ROLE_USER");
                userRepository.save(u);
            }
        });

        // 3. 创建默认管理员账号（如果不存在）
        User admin = ensureUser("admin", "admin123", "系统管理员", "admin@music.com", "ROLE_ADMIN");
        User user = ensureUser("user", "user123", "普通用户", "user@music.com", "ROLE_USER");

        // 4. 初始化歌單（如果不存在）
        initPlaylists(admin, user);
    }

    private User ensureUser(String username, String password, String nickname, String email, String role) {
        return transactionTemplate.execute(status ->
            userRepository.findByUsername(username).orElseGet(() -> {
                User u = new User();
                u.setUsername(username);
                u.setPassword(passwordEncoder.encode(password));
                u.setNickname(nickname);
                u.setEmail(email);
                u.setRole(role);
                User saved = userRepository.save(u);
                System.out.println("✅ 默認賬號已創建：" + username + " / " + password);
                return saved;
            })
        );
    }

    private void initPlaylists(User admin, User normalUser) {
        transactionTemplate.execute(status -> {
            if (playlistRepository.count() > 0) {
                System.out.println("ℹ️ 歌單數據已存在，跳過種子化");
                return null;
            }
            System.out.println("🚀 開始初始化示例歌單...");

            // 歌單定義 (name, description, coverUrl, owner, playCount)
            // 後面的 Map 存 playlistId -> List<songId>
            Object[][] defs = {
                {"周杰倫金曲精選", "Jay 出道以來最好聽的 14 首作品", "https://picsum.photos/seed/pl1/400/400", admin, 5680L},
                {"民謠搖籃曲", "趙雷、樸樹、宋冬野、毛不易陪你度過安靜的夜晚", "https://picsum.photos/seed/pl2/400/400", normalUser, 3240L},
                {"搖滾永恆經典", "Beyond、黑豹、Queen、Eagles、披頭士... 熱血沸騰的搖滾", "https://picsum.photos/seed/pl3/400/400", admin, 4890L},
                {"輕音樂放鬆時刻", "李閏珉、久石讓、Pachelbel、巴赫", "https://picsum.photos/seed/pl4/400/400", normalUser, 2150L},
                {"K歌必點金曲", "陳奕迅、張學友、林俊傑、梁靜茹", "https://picsum.photos/seed/pl5/400/400", admin, 7320L},
                {"古風中國風", "許嵩、HITA、張碧晨、周杰倫的東方韻味", "https://picsum.photos/seed/pl6/400/400", normalUser, 1890L},
            };

            // 每張歌單的 song_id 列表
            Map<Integer, int[]> songsMap = new HashMap<>();
            songsMap.put(0, new int[]{1, 2, 9, 15, 17, 18, 29, 30, 34, 41, 42, 57, 61, 66});
            songsMap.put(1, new int[]{5, 6, 13, 21, 22, 46, 47});
            songsMap.put(2, new int[]{7, 8, 23, 24, 50, 51, 54, 55, 56});
            songsMap.put(3, new int[]{16, 39, 40, 67, 68});
            songsMap.put(4, new int[]{19, 20, 31, 32, 33, 48, 49, 58, 63, 64, 65});
            songsMap.put(5, new int[]{12, 18, 29, 30, 36, 37, 45});

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime[] dates = {
                now.minusDays(30), now.minusDays(22), now.minusDays(15),
                now.minusDays(10), now.minusDays(5), now.minusDays(3)
            };

            for (int i = 0; i < defs.length; i++) {
                Playlist pl = new Playlist();
                pl.setName((String) defs[i][0]);
                pl.setDescription((String) defs[i][1]);
                pl.setCoverUrl((String) defs[i][2]);
                pl.setUser((User) defs[i][3]);
                pl.setIsPublic(true);
                pl.setPlayCount((Long) defs[i][4]);
                pl.setCreatedAt(dates[i]);
                pl.setUpdatedAt(now);
                Playlist saved = playlistRepository.save(pl);

                int[] songIds = songsMap.get(i);
                for (int j = 0; j < songIds.length; j++) {
                    Song song = songRepository.findById((long) songIds[j]).orElse(null);
                    if (song != null) {
                        PlaylistSong ps = new PlaylistSong();
                        ps.setPlaylistId(saved.getId());
                        ps.setSongId(song.getId());
                        ps.setSortOrder(j + 1);
                        ps.setAddedAt(now);
                        playlistSongRepository.save(ps);
                    }
                }
                System.out.println("  📋 歌單 #" + (i + 1) + " 已創建：" + pl.getName() + " (" + songIds.length + " 首)");
            }

            System.out.println("✅ 示例歌單初始化完成！");
            return null;
        });
    }

    private void migrateRoleColumn() {
        try {
            transactionTemplate.execute(status -> {
                entityManager.createNativeQuery(
                    "ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'ROLE_USER' AFTER nickname"
                ).executeUpdate();
                return null;
            });
            System.out.println("✅ 数据库迁移：users 表已添加 role 列");
        } catch (Exception e) {
            System.out.println("ℹ️ 数据库迁移：role 列已存在，跳过");
        }
    }
}
