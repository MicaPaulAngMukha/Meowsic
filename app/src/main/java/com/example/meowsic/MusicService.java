package com.example.meowsic;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import com.example.meowsic.model.Song;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private MediaPlayer player;
    private List<Song> songs;
    private int songPos;
    private final IBinder musicBind = new MusicBinder();
    private float playbackSpeed = 1.0f;
    
    private boolean shuffle = false;
    private Random rand;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;
    private int repeatMode = REPEAT_NONE;

    @Override
    public void onCreate() {
        super.onCreate();
        songPos = 0;
        player = new MediaPlayer();
        rand = new Random();
        initMusicPlayer();
    }

    public void initMusicPlayer() {
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    public void setList(List<Song> theSongs) {
        songs = theSongs;
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        player.stop();
        player.release();
        return false;
    }

    public void playSong() {
        player.reset();
        if (songs == null || songs.isEmpty()) return;
        Song playSong = songs.get(songPos);
        try {
            player.setDataSource(playSong.getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        player.prepareAsync();
    }

    public void setSong(int songIndex) {
        songPos = songIndex;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (player.getCurrentPosition() > 0) {
            if (repeatMode == REPEAT_ONE) {
                playSong();
            } else {
                playNext();
            }
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        applyPlaybackSpeed();
        mp.start();
    }

    public int getPosn() {
        return player.getCurrentPosition();
    }

    public int getDur() {
        try {
            return player.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isPng() {
        try {
            return player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public void pausePlayer() {
        player.pause();
    }

    public void seek(int posn) {
        player.seekTo(posn);
    }

    public void go() {
        player.start();
    }

    public void playPrev() {
        songPos--;
        if (songPos < 0) songPos = songs.size() - 1;
        playSong();
    }

    public void playNext() {
        if (shuffle) {
            int newSong = songPos;
            while (newSong == songPos && songs.size() > 1) {
                newSong = rand.nextInt(songs.size());
            }
            songPos = newSong;
        } else {
            songPos++;
            if (songPos >= songs.size()) {
                if (repeatMode == REPEAT_ALL) {
                    songPos = 0;
                } else {
                    songPos = songs.size() - 1;
                    return; // Stop at the end if not repeating all
                }
            }
        }
        playSong();
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        applyPlaybackSpeed();
    }

    public Song getCurrentSong() {
        if (songs != null && songPos >= 0 && songPos < songs.size()) {
            return songs.get(songPos);
        }
        return null;
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void nextRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    private void applyPlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (player != null && (isPng() || player.getDuration() > 0)) {
                    PlaybackParams params = player.getPlaybackParams();
                    params.setSpeed(playbackSpeed);
                    player.setPlaybackParams(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}