package com.example.meowsic.model;

import android.net.Uri;
import java.io.Serializable;

public class Song implements Serializable {
    private long id;
    private String title;
    private String artist;
    private String album;
    private long duration;
    private String path;
    private Uri albumArtUri;

    public Song(long id, String title, String artist, String album, long duration, String path, Uri albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.path = path;
        this.albumArtUri = albumArtUri;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public long getDuration() { return duration; }
    public String getPath() { return path; }
    public Uri getAlbumArtUri() { return albumArtUri; }
}