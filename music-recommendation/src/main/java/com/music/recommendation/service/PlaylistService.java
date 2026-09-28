package com.music.recommendation.service;

import com.music.recommendation.entity.Playlist;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * 歌单服务接口
 */
public interface PlaylistService {

    /** 创建歌单 */
    Playlist createPlaylist(String name, String description, User user);

    /** 获取用户的歌单 */
    List<Playlist> getUserPlaylists(Long userId);

    /** 获取热门歌单 */
    List<Playlist> getHotPlaylists(int limit);

    /** 获取歌单详情 */
    Optional<Playlist> getPlaylistById(Long id);

    /** 获取歌单中的歌曲 */
    List<Song> getPlaylistSongs(Long playlistId);

    /** 添加歌曲到歌单 */
    boolean addSongToPlaylist(Long playlistId, Long songId);

    /** 从歌单移除歌曲 */
    void removeSongFromPlaylist(Long playlistId, Long songId);

    /** 删除歌单 */
    void deletePlaylist(Long id);

    /** 搜索公开歌单 */
    List<Playlist> searchPlaylists(String keyword);
}
