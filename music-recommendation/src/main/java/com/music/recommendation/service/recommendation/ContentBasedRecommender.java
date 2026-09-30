package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于内容的推荐引擎
 * 通过歌曲的 genre（风格）、artist（歌手）构建特征向量，
 * 使用余弦相似度计算歌曲之间的相似度，推荐与用户历史偏好相似的歌曲。
 */
@Component
public class ContentBasedRecommender {

    private final UserSongInteractionRepository interactionRepository;
    private final SongRepository songRepository;

    public ContentBasedRecommender(UserSongInteractionRepository interactionRepository,
                                   SongRepository songRepository) {
        this.interactionRepository = interactionRepository;
        this.songRepository = songRepository;
    }

    /** 特征权重 */
    private static final double GENRE_WEIGHT = 0.5;
    private static final double ARTIST_WEIGHT = 0.4;
    private static final double ALBUM_WEIGHT = 0.1;

    /**
     * 为用户生成基于内容的推荐
     * @param userId 用户ID
     * @param limit 推荐数量
     * @return 推荐歌曲列表
     */
    public List<Song> recommend(Long userId, int limit) {
        // 1. 获取用户交互过的所有歌曲
        List<UserSongInteraction> interactions = interactionRepository.findByUserId(userId);
        if (interactions.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取用户交互过的歌曲ID
        Set<Long> interactedSongIds = interactions.stream()
                .map(UserSongInteraction::getSongId)
                .collect(Collectors.toSet());

        // 2. 批量加载所有交互歌曲，避免循环内 findById（N+1 优化）
        Map<Long, Song> songCache = new HashMap<>();
        songRepository.findAllById(interactedSongIds).forEach(s -> songCache.put(s.getId(), s));

        // 3. 构建用户兴趣特征向量（加权平均交互歌曲的特征）
        Map<String, Double> userGenreProfile = new HashMap<>();
        Map<String, Double> userArtistProfile = new HashMap<>();
        Set<String> userAlbums = new HashSet<>();

        for (UserSongInteraction interaction : interactions) {
            Song song = songCache.get(interaction.getSongId());
            if (song == null) continue;

            double weight = computeInteractionWeight(interaction);

            userGenreProfile.merge(song.getGenre(), weight, Double::sum);
            userArtistProfile.merge(song.getArtist(), weight, Double::sum);
            if (song.getAlbum() != null) {
                userAlbums.add(song.getAlbum());
            }
        }

        // 3. 归一化用户兴趣特征
        normalizeProfile(userGenreProfile);
        normalizeProfile(userArtistProfile);

        // 4. 获取候选歌曲（除去用户已交互的），限制批次避免全表扫描
        int total = (int) songRepository.count();
        int batch = Math.min(total, limit * 10);
        List<Song> allSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));
        List<Song> candidates = allSongs.stream()
                .filter(s -> !interactedSongIds.contains(s.getId()))
                .collect(Collectors.toList());

        // 5. 为每首候选歌曲计算与用户兴趣的相似度
        Map<Long, Double> scoreMap = new HashMap<>();
        for (Song candidate : candidates) {
            double genreScore = getFeatureScore(userGenreProfile, candidate.getGenre());
            double artistScore = getFeatureScore(userArtistProfile, candidate.getArtist());
            double albumScore = userAlbums.contains(candidate.getAlbum()) ? 1.0 : 0.0;

            double totalScore = GENRE_WEIGHT * genreScore
                    + ARTIST_WEIGHT * artistScore
                    + ALBUM_WEIGHT * albumScore;

            if (totalScore > 0) {
                scoreMap.put(candidate.getId(), totalScore);
            }
        }

        // 6. 按分数排序返回（从已加载的 candidates Map 中取值，避免 N+1 findById）
        Map<Long, Song> candidateMap = candidates.stream()
                .collect(Collectors.toMap(Song::getId, s -> s));
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> candidateMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 计算交互权重（播放次数多、点赞的歌曲权重更高）
     */
    private double computeInteractionWeight(UserSongInteraction interaction) {
        double weight = 1.0;
        weight += Math.log1p(interaction.getPlayCount()) * 0.5;
        if (interaction.getIsLiked()) {
            weight += 2.0;
        }
        return weight;
    }

    /**
     * 归一化特征向量（将值映射到 0~1 范围）
     */
    private void normalizeProfile(Map<String, Double> profile) {
        double max = profile.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (max > 0) {
            profile.replaceAll((key, value) -> value / max);
        }
    }

    /**
     * 获取用户在某个特征上的兴趣分数
     */
    private double getFeatureScore(Map<String, Double> profile, String feature) {
        return profile.getOrDefault(feature, 0.0);
    }

    /**
     * 根据歌曲ID获取相似歌曲推荐
     * @param songId 当前歌曲ID
     * @param limit 推荐数量
     * @return 相似歌曲列表
     */
    public List<Song> getRelatedSongs(Long songId, int limit) {
        Optional<Song> songOpt = songRepository.findById(songId);
        if (songOpt.isEmpty()) return Collections.emptyList();

        Song currentSong = songOpt.get();

        // 取一批候选歌曲，限制数量避免全表扫描
        int total = (int) songRepository.count();
        int batch = Math.min(total, limit * 10);
        List<Song> allSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));
        List<Song> candidates = allSongs.stream()
                .filter(s -> !s.getId().equals(songId))
                .collect(Collectors.toList());

        Map<Long, Double> scoreMap = new HashMap<>();
        for (Song candidate : candidates) {
            double genreScore = currentSong.getGenre().equals(candidate.getGenre()) ? 1.0 : 0.0;
            double artistScore = currentSong.getArtist().equals(candidate.getArtist()) ? 1.0 : 0.0;
            double albumScore = currentSong.getAlbum() != null
                    && currentSong.getAlbum().equals(candidate.getAlbum()) ? 1.0 : 0.0;

            double totalScore = GENRE_WEIGHT * genreScore
                    + ARTIST_WEIGHT * artistScore
                    + ALBUM_WEIGHT * albumScore;

            if (totalScore > 0) {
                scoreMap.put(candidate.getId(), totalScore);
            }
        }

        // 从已加载的 candidates Map 取值，避免 N+1 findById
        Map<Long, Song> candidateMap = candidates.stream()
                .collect(Collectors.toMap(Song::getId, s -> s));
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> candidateMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}