package com.music.recommendation.service;

import com.music.recommendation.entity.Playlist;
import com.music.recommendation.entity.PlaylistSong;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.PlaylistRepository;
import com.music.recommendation.repository.PlaylistSongRepository;
import com.music.recommendation.repository.SongRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 歌单服务实现
 */
@Service
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final SongRepository songRepository;

    public PlaylistServiceImpl(PlaylistRepository playlistRepository,
                               PlaylistSongRepository playlistSongRepository,
                               SongRepository songRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistSongRepository = playlistSongRepository;
        this.songRepository = songRepository;
    }

    /** 创建歌单 */
    @Override
    public Playlist createPlaylist(String name, String description, User user) {
        Playlist playlist = new Playlist();
        playlist.setName(name);
        playlist.setDescription(description);
        playlist.setUser(user);
        return playlistRepository.save(playlist);
    }

    /** 获取用户的歌单 */
    @Override
    public List<Playlist> getUserPlaylists(Long userId) {
        return playlistRepository.findByUserId(userId);
    }

    /** 获取热门歌单 */
    @Override
    public List<Playlist> getHotPlaylists(int limit) {
        return playlistRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, limit));
    }

    /** 获取歌单详情 */
    @Override
    public Optional<Playlist> getPlaylistById(Long id) {
        return playlistRepository.findById(id);
    }

    /** 获取歌单中的歌曲 */
    @Override
    public List<Song> getPlaylistSongs(Long playlistId) {
        List<PlaylistSong> playlistSongs = playlistSongRepository.findByPlaylistIdOrderBySortOrderAsc(playlistId);
        List<Song> songs = new ArrayList<>();
        for (PlaylistSong ps : playlistSongs) {
            songRepository.findById(ps.getSongId()).ifPresent(songs::add);
        }
        return songs;
    }

    /** 添加歌曲到歌单 */
    @Override
    @Transactional
    public boolean addSongToPlaylist(Long playlistId, Long songId) {
        if (playlistSongRepository.existsByPlaylistIdAndSongId(playlistId, songId)) {
            return false;
        }
        PlaylistSong playlistSong = new PlaylistSong();
        playlistSong.setPlaylistId(playlistId);
        playlistSong.setSongId(songId);
        long count = playlistSongRepository.countByPlaylistId(playlistId);
        playlistSong.setSortOrder((int) count);
        playlistSongRepository.save(playlistSong);
        return true;
    }

    /** 从歌单移除歌曲 */
    @Override
    @Transactional
    public void removeSongFromPlaylist(Long playlistId, Long songId) {
        playlistSongRepository.deleteByPlaylistIdAndSongId(playlistId, songId);
    }

    /** 删除歌单 */
    @Override
    @Transactional
    public void deletePlaylist(Long id) {
        playlistRepository.deleteById(id);
    }

    /** 搜索公开歌单 */
    @Override
    public List<Playlist> searchPlaylists(String keyword) {
        return playlistRepository.findByNameContainingAndIsPublicTrue(keyword);
    }
}
