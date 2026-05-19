package com.example.meowsic.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlist_songs")
public class SongEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long mediaStoreId;
    public int playlistId;
    public int albumId; // For custom albums

    public SongEntity(long mediaStoreId, int playlistId, int albumId) {
        this.mediaStoreId = mediaStoreId;
        this.playlistId = playlistId;
        this.albumId = albumId;
    }
}