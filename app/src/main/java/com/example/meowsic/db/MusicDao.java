package com.example.meowsic.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MusicDao {
    @Insert
    void insertPlaylist(Playlist playlist);

    @Delete
    void deletePlaylist(Playlist playlist);

    @Query("SELECT * FROM playlists")
    List<Playlist> getAllPlaylists();

    @Insert
    void insertAlbum(Album album);

    @Delete
    void deleteAlbum(Album album);

    @Query("SELECT * FROM albums")
    List<Album> getAllAlbums();

    @Insert
    void insertSongToPlaylist(SongEntity songEntity);

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    List<SongEntity> getSongsInPlaylist(int playlistId);

    @Query("DELETE FROM playlist_songs WHERE mediaStoreId = :songId AND playlistId = :playlistId")
    void removeSongFromPlaylist(long songId, int playlistId);
}