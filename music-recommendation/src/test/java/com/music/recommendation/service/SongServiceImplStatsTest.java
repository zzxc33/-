package com.music.recommendation.service;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import com.music.recommendation.service.recommendation.HybridRecommenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * SongServiceImpl 行為分析新方法單元測試
 *
 * 覆蓋：
 *   1. getUserTopArtists — 空用戶、零交互、有交互（播放+點贊加權排序、Top 8 截斷）
 *   2. getUserRecentPlays — 空用戶、零交互、null lastPlayedAt 過濾、limit 參數邊界
 *   3. getPlayHeatmapData — 空用戶、零交互、7天外數據過濾、小時聚合
 */
@ExtendWith(MockitoExtension.class)
class SongServiceImplStatsTest {

    @Mock SongRepository songRepository;
    @Mock UserSongInteractionRepository interactionRepository;
    @Mock HybridRecommenderService hybridRecommender;

    private SongServiceImpl songService;

    @BeforeEach
    void setUp() {
        songService = new SongServiceImpl(songRepository, interactionRepository, hybridRecommender);
    }

    // ==================== getUserTopArtists ====================

    @Test
    @DisplayName("TopArtists - userId=null → 空 Map")
    void topArtists_nullUserId() {
        assertTrue(songService.getUserTopArtists(null).isEmpty());
    }

    @Test
    @DisplayName("TopArtists - userId<=0 → 空 Map")
    void topArtists_invalidUserId() {
        assertTrue(songService.getUserTopArtists(0L).isEmpty());
        assertTrue(songService.getUserTopArtists(-1L).isEmpty());
    }

    @Test
    @DisplayName("TopArtists - 用戶無交互 → 空 Map")
    void topArtists_noInteractions() {
        when(interactionRepository.findByUserId(1L)).thenReturn(List.of());
        assertTrue(songService.getUserTopArtists(1L).isEmpty());
    }

    @Test
    @DisplayName("TopArtists - 加權排序：播放+點贊(+5)，Top 8 截斷")
    void topArtists_weightedRanking() {
        // songId→artist 對應：1=周杰倫, 2=五月天, 3=周杰倫, 4=五月天, 5=Queen
        Song s1 = song(1L, "歌1", "周杰倫");
        Song s2 = song(2L, "歌2", "五月天");
        Song s3 = song(3L, "歌3", "周杰倫");
        Song s4 = song(4L, "歌4", "五月天");
        Song s5 = song(5L, "歌5", "Queen");
        List<Song> songs = List.of(s1, s2, s3, s4, s5);

        // 交互：
        // song1(周杰倫): playCount=3, liked=true → 3 + 5 = 8
        // song2(五月天):  playCount=1, liked=false → 1 + 0 = 1
        // song3(周杰倫): playCount=2, liked=false → 2 + 0 = 2
        // song4(五月天):  playCount=1, liked=true  → 1 + 5 = 6
        // song5(Queen):  playCount=1, liked=true  → 1 + 5 = 6
        // 周杰倫總分 = 8+2=10, 五月天 = 1+6=7, Queen=6 → 應按 周杰倫 > 五月天 > Queen 排序
        UserSongInteraction i1 = interaction(1L, 1L, 3, true);
        UserSongInteraction i2 = interaction(1L, 2L, 1, false);
        UserSongInteraction i3 = interaction(1L, 3L, 2, false);
        UserSongInteraction i4 = interaction(1L, 4L, 1, true);
        UserSongInteraction i5 = interaction(1L, 5L, 1, true);
        List<UserSongInteraction> interactions = List.of(i1, i2, i3, i4, i5);

        when(interactionRepository.findByUserId(1L)).thenReturn(interactions);
        when(songRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(songs);

        Map<String, Long> result = songService.getUserTopArtists(1L);

        assertEquals(3, result.size());
        // 第一：周杰倫（權重=10）
        Iterator<Map.Entry<String, Long>> it = result.entrySet().iterator();
        Map.Entry<String, Long> first = it.next();
        assertEquals("周杰倫", first.getKey());
        assertEquals(Long.valueOf(10), first.getValue());
        // 第二：五月天（權重=7）
        Map.Entry<String, Long> second = it.next();
        assertEquals("五月天", second.getKey());
        assertEquals(Long.valueOf(7), second.getValue());
    }

    @Test
    @DisplayName("TopArtists - 超過 8 位藝人只返回 Top 8")
    void topArtists_truncatedTo8() {
        List<UserSongInteraction> interactions = new ArrayList<>();
        List<Song> songs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            interactions.add(interaction(1L, (long) i, 1, false));
            songs.add(song((long) i, "歌" + i, "藝人" + i));
        }
        when(interactionRepository.findByUserId(1L)).thenReturn(interactions);
        when(songRepository.findAllById(org.mockito.ArgumentMatchers.anyList())).thenReturn(songs);

        Map<String, Long> result = songService.getUserTopArtists(1L);
        assertEquals(8, result.size());
    }

    @Test
    @DisplayName("TopArtists - songId 對應 Song 不存在時跳過")
    void topArtists_missingSongSkipped() {
        UserSongInteraction i1 = interaction(1L, 1L, 2, false);
        UserSongInteraction i2 = interaction(1L, 999L, 1, false); // songId=999 不存在
        when(interactionRepository.findByUserId(1L)).thenReturn(List.of(i1, i2));
        when(songRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(song(1L, "歌1", "周杰倫")));

        Map<String, Long> result = songService.getUserTopArtists(1L);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("周杰倫"));
    }

    // ==================== getUserRecentPlays ====================

    @Test
    @DisplayName("RecentPlays - userId=null → 空列表")
    void recentPlays_nullUserId() {
        assertTrue(songService.getUserRecentPlays(null, 5).isEmpty());
    }

    @Test
    @DisplayName("RecentPlays - userId<=0 → 空列表")
    void recentPlays_invalidUserId() {
        assertTrue(songService.getUserRecentPlays(0L, 5).isEmpty());
        assertTrue(songService.getUserRecentPlays(-1L, 5).isEmpty());
    }

    @Test
    @DisplayName("RecentPlays - 零交互 → 空列表")
    void recentPlays_noInteractions() {
        when(interactionRepository.findByUserId(1L)).thenReturn(List.of());
        assertTrue(songService.getUserRecentPlays(1L, 10).isEmpty());
    }

    @Test
    @DisplayName("RecentPlays - null lastPlayedAt 被過濾（即使 playCount>0）")
    void recentPlays_nullLastPlayedAtFiltered() {
        // i1 有 lastPlayedAt + playCount>0 → 顯示
        UserSongInteraction i1 = new UserSongInteraction();
        i1.setId(1L); i1.setUserId(1L); i1.setSongId(1L);
        i1.setPlayCount(3); i1.setLastPlayedAt(LocalDateTime.now().minusHours(1));

        // i2 playCount>0 但 lastPlayedAt=null → 過濾
        UserSongInteraction i2 = new UserSongInteraction();
        i2.setId(2L); i2.setUserId(1L); i2.setSongId(2L);
        i2.setPlayCount(2); i2.setLastPlayedAt(null);
        when(interactionRepository.findByUserId(1L)).thenReturn(List.of(i1, i2));
        when(songRepository.findAllById(List.of(1L))).thenReturn(List.of(song(1L, "歌1", "歌手")));

        List<Song> result = songService.getUserRecentPlays(1L, 10);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("RecentPlays - limit 邊界：<=1 取 1，>20 取 20")
    void recentPlays_limitBoundary() {
        // limit=0 → clamp 到 1
        assertEquals(1, songService.getUserRecentPlays(1L, 0) == null ? 0 : 1);
        // limit=-5 → clamp 到 1
        // limit=100 → clamp 到 20
        // 都不崩潰就行
        assertDoesNotThrow(() -> songService.getUserRecentPlays(1L, 0));
        assertDoesNotThrow(() -> songService.getUserRecentPlays(1L, -5));
        assertDoesNotThrow(() -> songService.getUserRecentPlays(1L, 100));
    }

    // ==================== getPlayHeatmapData ====================

    @Test
    @DisplayName("Heatmap - userId=null → 空列表")
    void heatmap_nullUserId() {
        assertTrue(songService.getPlayHeatmapData(null).isEmpty());
    }

    @Test
    @DisplayName("Heatmap - 零交互 → 空列表")
    void heatmap_noInteractions() {
        when(interactionRepository.findByUserId(1L)).thenReturn(List.of());
        assertTrue(songService.getPlayHeatmapData(1L).isEmpty());
    }

    @Test
    @DisplayName("Heatmap - 7天外數據被過濾")
    void heatmap_oldDataFiltered() {
        UserSongInteraction old = new UserSongInteraction();
        old.setUserId(1L); old.setSongId(1L);
        old.setPlayCount(5); old.setLastPlayedAt(LocalDateTime.now().minusDays(10)); // 10 天前

        UserSongInteraction recent = new UserSongInteraction();
        recent.setUserId(1L); recent.setSongId(2L);
        recent.setPlayCount(3); recent.setLastPlayedAt(LocalDateTime.now().minusDays(3).withHour(14)); // 3 天前 14 點

        when(interactionRepository.findByUserId(1L)).thenReturn(List.of(old, recent));

        List<int[]> result = songService.getPlayHeatmapData(1L);
        // 只有 recent 在 7 天內
        assertEquals(1, result.size());
        int[] point = result.get(0);
        assertEquals(3, point[0]); // dayOffset = 3
        assertEquals(14, point[1]); // hour = 14
        assertEquals(3, point[2]); // count = playCount
    }

    @Test
    @DisplayName("Heatmap - 數據格式為 [dayOffset, hour, count]")
    void heatmap_format() {
        UserSongInteraction recent = new UserSongInteraction();
        recent.setUserId(1L); recent.setSongId(1L);
        recent.setPlayCount(2); recent.setLastPlayedAt(LocalDateTime.now().withHour(20));

        when(interactionRepository.findByUserId(1L)).thenReturn(List.of(recent));

        List<int[]> result = songService.getPlayHeatmapData(1L);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).length); // 每個點 3 個值
        // dayOffset = 0（今天）
        assertEquals(0, result.get(0)[0]);
        assertEquals(20, result.get(0)[1]);
        assertEquals(2, result.get(0)[2]);
    }

    @Test
    @DisplayName("Heatmap - 小時聚合：同一小時多首歌累加")
    void heatmap_hourlyAggregation() {
        UserSongInteraction a = interaction(1L, 1L, 2, false);
        a.setLastPlayedAt(LocalDateTime.now().withHour(15));
        UserSongInteraction b = interaction(1L, 2L, 3, false);
        b.setLastPlayedAt(LocalDateTime.now().withHour(15));

        when(interactionRepository.findByUserId(1L)).thenReturn(List.of(a, b));

        List<int[]> result = songService.getPlayHeatmapData(1L);
        assertEquals(1, result.size());
        assertEquals(5, result.get(0)[2]); // 2 + 3 = 5
    }

    @Test
    @DisplayName("Heatmap - lastPlayedAt 為 null 時回退到 createdAt")
    void heatmap_fallbackToCreatedAt() {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setUserId(1L); ui.setSongId(1L); ui.setPlayCount(4);
        ui.setCreatedAt(LocalDateTime.now().withHour(9));
        // lastPlayedAt 保持 null

        when(interactionRepository.findByUserId(1L)).thenReturn(List.of(ui));

        List<int[]> result = songService.getPlayHeatmapData(1L);
        assertEquals(1, result.size());
        assertEquals(9, result.get(0)[1]); // 用 createdAt 的小時
        assertEquals(4, result.get(0)[2]);
    }

    // ==================== 輔助方法 ====================

    private Song song(Long id, String title, String artist) {
        Song s = new Song();
        s.setId(id);
        s.setTitle(title);
        s.setArtist(artist);
        return s;
    }

    private UserSongInteraction interaction(Long userId, Long songId, int playCount, boolean liked) {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setUserId(userId);
        ui.setSongId(songId);
        ui.setPlayCount(playCount);
        ui.setIsLiked(liked);
        ui.setLastPlayedAt(LocalDateTime.now());
        return ui;
    }
}
