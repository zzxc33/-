package com.music.recommendation.service;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import com.music.recommendation.service.recommendation.HybridRecommenderService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 歌曲服务实现
 */
@Service
public class SongServiceImpl implements SongService {

    private final SongRepository songRepository;
    private final UserSongInteractionRepository interactionRepository;
    private final HybridRecommenderService hybridRecommender;

    public SongServiceImpl(SongRepository songRepository,
                           UserSongInteractionRepository interactionRepository,
                           HybridRecommenderService hybridRecommender) {
        this.songRepository = songRepository;
        this.interactionRepository = interactionRepository;
        this.hybridRecommender = hybridRecommender;
    }

    /** 获取所有歌曲 */
    @Override
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    /** 分页获取歌曲（按 id 倒序） */
    @Override
    @Cacheable(cacheNames = "songs", key = "'page:'+#page+':'+#size")
    public Map<String, Object> getSongsPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Song> result = songRepository.findAll(pageable);
        Map<String, Object> pageInfo = new LinkedHashMap<>();
        pageInfo.put("content", result.getContent());
        pageInfo.put("page", result.getNumber());
        pageInfo.put("size", result.getSize());
        pageInfo.put("totalElements", result.getTotalElements());
        pageInfo.put("totalPages", result.getTotalPages());
        pageInfo.put("hasNext", result.hasNext());
        pageInfo.put("hasPrevious", result.hasPrevious());
        return pageInfo;
    }

    /** 根据ID获取歌曲 */
    @Override
    @Cacheable(cacheNames = "songs", key = "'song:'+#id")
    public Optional<Song> getSongById(Long id) {
        return songRepository.findById(id);
    }

    /** 搜索歌曲 */
    @Override
    public List<Song> searchSongs(String keyword) {
        return songRepository.searchByKeyword(keyword, PageRequest.of(0, 20));
    }

    /** 获取热门歌曲 */
    @Override
    @Cacheable(cacheNames = "songs", key = "'hot:'+#limit")
    public List<Song> getHotSongs(int limit) {
        return songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, limit));
    }

    /** 获取最新歌曲 */
    @Override
    @Cacheable(cacheNames = "songs", key = "'new:'+#limit")
    public List<Song> getNewSongs(int limit) {
        return songRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    /** 获取推荐歌曲（混合推荐引擎） */
    @Override
    public List<Song> getRecommendedSongs(Long userId, int limit) {
        return hybridRecommender.recommend(userId, limit);
    }

    /** 根据风格获取歌曲 */
    @Override
    @Cacheable(cacheNames = "songs", key = "'genre:'+#genre")
    public List<Song> getSongsByGenre(String genre) {
        return songRepository.findByGenre(genre);
    }

    /** 记录用户播放歌曲 */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "songs", allEntries = true)
    public void recordPlay(Long userId, Long songId) {
        songRepository.findById(songId).ifPresent(song -> {
            song.setPlayCount(song.getPlayCount() + 1);
            songRepository.save(song);
        });

        if (userId > 0) {
            UserSongInteraction interaction = interactionRepository
                    .findByUserIdAndSongId(userId, songId)
                    .orElse(new UserSongInteraction());
            interaction.setUserId(userId);
            interaction.setSongId(songId);
            interaction.setPlayCount(interaction.getPlayCount() + 1);
            interaction.setLastPlayedAt(LocalDateTime.now());
            interactionRepository.save(interaction);
        }
    }

    /** 切换用户点赞歌曲 */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "songs", allEntries = true)
    public void recordLike(Long userId, Long songId) {
        if (userId <= 0) return;

        UserSongInteraction interaction = interactionRepository
                .findByUserIdAndSongId(userId, songId)
                .orElse(new UserSongInteraction());

        boolean wasLiked = Boolean.TRUE.equals(interaction.getIsLiked());

        songRepository.findById(songId).ifPresent(song -> {
            if (wasLiked) {
                song.setLikeCount(Math.max(0, song.getLikeCount() - 1));
            } else {
                song.setLikeCount(song.getLikeCount() + 1);
            }
            songRepository.save(song);
        });

        interaction.setUserId(userId);
        interaction.setSongId(songId);
        interaction.setIsLiked(!wasLiked);
        if (!wasLiked) {
            interaction.setLikedAt(LocalDateTime.now());
        }
        interactionRepository.save(interaction);
    }

    /** 获取相关歌曲 */
    @Override
    public List<Song> getRelatedSongs(Long songId, int limit) {
        Song currentSong = songRepository.findById(songId).orElse(null);
        if (currentSong == null) {
            return songRepository.findAllByOrderByLikeCountDesc(PageRequest.of(0, limit));
        }

        String genre = currentSong.getGenre();
        List<Song> results = new ArrayList<>();

        if (genre != null && !genre.isEmpty()) {
            List<Song> sameGenreAll = songRepository.findByGenre(genre);
            List<Song> sameGenre = sameGenreAll.stream()
                .filter(s -> !s.getId().equals(songId))
                .collect(Collectors.toList());
            Collections.shuffle(sameGenre, new Random());
            results.addAll(sameGenre.subList(0, Math.min(limit, sameGenre.size())));
        }

        if (results.size() < limit) {
            Set<Long> existingIds = results.stream().map(Song::getId).collect(Collectors.toSet());
            existingIds.add(songId);
            List<Song> hotSongs = songRepository.findAllByOrderByLikeCountDesc(PageRequest.of(0, limit * 2));
            List<Song> shuffledHot = new ArrayList<>(hotSongs);
            Collections.shuffle(shuffledHot, new Random());
            for (Song s : shuffledHot) {
                if (!existingIds.contains(s.getId())) {
                    results.add(s);
                    existingIds.add(s.getId());
                }
                if (results.size() >= limit) break;
            }
        }

        return results.size() > limit ? results.subList(0, limit) : results;
    }

    /** 获取所有风格 */
    @Override
    @Cacheable(cacheNames = "songs", key = "'genres'")
    public List<String> getAllGenres() {
        return songRepository.findDistinctGenres();
    }

    /** 统计用户总播放次数 */
    @Override
    public long countTotalPlayed(Long userId) {
        if (userId == null || userId <= 0) return 0;
        return interactionRepository.sumPlayCountByUserId(userId);
    }

    /** 统计用户喜欢的歌曲数 */
    @Override
    public long countTotalLiked(Long userId) {
        if (userId == null || userId <= 0) return 0;
        return interactionRepository.countByUserIdAndIsLikedTrue(userId);
    }

    /** 获取用户喜欢的歌曲列表 */
    @Override
    public List<Song> getLikedSongs(Long userId) {
        if (userId == null || userId <= 0) return Collections.emptyList();
        List<Long> ids = interactionRepository.findLikedSongIdsByUserId(userId);
        if (ids.isEmpty()) return Collections.emptyList();
        return songRepository.findAllById(ids);
    }

    /** 检查用户是否已点赞某首歌 */
    @Override
    public boolean isLiked(Long userId, Long songId) {
        if (userId == null || userId <= 0) return false;
        return interactionRepository.findByUserIdAndSongId(userId, songId)
                .map(UserSongInteraction::getIsLiked)
                .orElse(false);
    }

    /** 根据歌曲ID批量获取歌曲 */
    @Override
    public List<Song> getSongsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return songRepository.findAllById(ids);
    }

    /** 统计用户风格分布 */
    @Override
    public Map<String, Long> getUserGenreStats(Long userId) {
        if (userId == null || userId <= 0) return new LinkedHashMap<>();
        List<UserSongInteraction> interactions = interactionRepository.findByUserId(userId);
        if (interactions.isEmpty()) return new LinkedHashMap<>();
        List<Long> songIds = interactions.stream().map(UserSongInteraction::getSongId).collect(Collectors.toList());
        List<Song> songs = songRepository.findAllById(songIds);
        Map<Long, String> genreMap = songs.stream().collect(Collectors.toMap(Song::getId, s -> s.getGenre() == null ? "其他" : s.getGenre(), (a, b) -> a));
        Map<String, Long> stat = new LinkedHashMap<>();
        for (UserSongInteraction it : interactions) {
            String genre = genreMap.getOrDefault(it.getSongId(), "其他");
            long weight = (it.getPlayCount() == null ? 0 : it.getPlayCount()) + (Boolean.TRUE.equals(it.getIsLiked()) ? 5 : 0);
            stat.merge(genre, Math.max(1, weight), Long::sum);
        }
        return stat.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** 根据歌手精确查找（按播放量排序） */
    @Override
    public List<Song> getSongsByArtist(String artist) {
        if (artist == null || artist.isBlank()) return Collections.emptyList();
        return songRepository.findByArtistOrderByPlayCountDesc(artist.trim());
    }

    /** 全局播放量排行榜 Top N */
    @Override
    public List<Song> getRankingByPlayCount(int limit) {
        int safe = Math.min(Math.max(limit, 1), 100);
        return songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, safe));
    }

    /** 随机获取一首歌曲 */
    @Override
    public Song getRandomSong() {
        long total = songRepository.count();
        if (total == 0) return null;
        int idx = (int) (Math.random() * total);
        return songRepository.findAll(PageRequest.of(idx, 1)).getContent().stream().findFirst().orElse(null);
    }

    /** 最愛歌手榜（按播放量+點贊加權排序） */
    @Override
    public Map<String, Long> getUserTopArtists(Long userId) {
        if (userId == null || userId <= 0) return new LinkedHashMap<>();
        List<UserSongInteraction> interactions = interactionRepository.findByUserId(userId);
        if (interactions.isEmpty()) return new LinkedHashMap<>();
        List<Long> songIds = interactions.stream().map(UserSongInteraction::getSongId).collect(Collectors.toList());
        List<Song> songs = songRepository.findAllById(songIds);
        Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));
        Map<String, Long> stat = new LinkedHashMap<>();
        for (UserSongInteraction it : interactions) {
            Song s = songMap.get(it.getSongId());
            if (s == null) continue;
            String artist = s.getArtist() != null ? s.getArtist() : "未知";
            long weight = (it.getPlayCount() == null ? 0 : it.getPlayCount()) + (Boolean.TRUE.equals(it.getIsLiked()) ? 5 : 0);
            stat.merge(artist, Math.max(1, weight), Long::sum);
        }
        return stat.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** 最近播放列表（按 lastPlayedAt 倒序） */
    @Override
    public List<Song> getUserRecentPlays(Long userId, int limit) {
        if (userId == null || userId <= 0) return Collections.emptyList();
        int safe = Math.min(Math.max(limit, 1), 20);
        List<UserSongInteraction> interactions = interactionRepository.findByUserId(userId);
        if (interactions.isEmpty()) return Collections.emptyList();
        // 按 lastPlayedAt 倒序，取有播放記錄的
        List<UserSongInteraction> played = interactions.stream()
                .filter(it -> it.getPlayCount() != null && it.getPlayCount() > 0 && it.getLastPlayedAt() != null)
                .sorted((a, b) -> b.getLastPlayedAt().compareTo(a.getLastPlayedAt()))
                .limit(safe)
                .collect(Collectors.toList());
        List<Long> songIds = played.stream().map(UserSongInteraction::getSongId).collect(Collectors.toList());
        return songRepository.findAllById(songIds);
    }

    /** 7天播放熱力圖數據（ECharts calendar heatmap 格式：[dayOffset, hour, count]） */
    @Override
    public List<int[]> getPlayHeatmapData(Long userId) {
        if (userId == null || userId <= 0) return Collections.emptyList();
        List<UserSongInteraction> interactions = interactionRepository.findByUserId(userId);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        // 統計每小時播放次數
        int[][] matrix = new int[7][24];
        for (UserSongInteraction it : interactions) {
            LocalDateTime time = it.getLastPlayedAt() != null ? it.getLastPlayedAt() : it.getCreatedAt();
            if (time == null || time.isBefore(sevenDaysAgo)) continue;
            int dayOffset = (int) java.time.temporal.ChronoUnit.DAYS.between(time.toLocalDate(), LocalDate.now());
            if (dayOffset < 0 || dayOffset >= 7) continue;
            int hour = time.getHour();
            int plays = it.getPlayCount() != null ? it.getPlayCount() : 0;
            matrix[dayOffset][hour] += plays;
        }
        List<int[]> result = new java.util.ArrayList<>();
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                if (matrix[d][h] > 0) result.add(new int[]{d, h, matrix[d][h]});
            }
        }
        return result;
    }
}
