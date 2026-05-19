package com.example.meowsic;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
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
import java.util.concurrent.CopyOnWriteArrayList;

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

    private final CopyOnWriteArrayList<MusicServiceListener> listeners = new CopyOnWriteArrayList<>();

    public interface MusicServiceListener {
        void onSongChanged(int songIndex, Song song);
        void onPlayerStateChanged(boolean isPlaying);
        void onPrepared();
    }

    public void addListener(MusicServiceListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(MusicServiceListener listener) {
        listeners.remove(listener);
    }

    // For compatibility with MainActivity.java
    public void setListener(MusicServiceListener listener) {
        if (listener == null) {
            listeners.clear();
        } else {
            addListener(listener);
        }
    }

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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player.setAudioAttributes(attr);
        }

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
        // Return true to allow rebind
        return true;
    }

    public void playSong() {
        if (player == null) return;
        player.reset();
        if (songs == null || songs.isEmpty()) return;
        if (songPos < 0) songPos = 0;
        if (songPos >= songs.size()) songPos = songs.size() - 1;
        
        Song playSong = songs.get(songPos);
        try {
            player.setDataSource(playSong.getPath());
            player.prepareAsync();
            
            for (MusicServiceListener listener : listeners) {
                listener.onSongChanged(songPos, playSong);
                listener.onPlayerStateChanged(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        for (MusicServiceListener listener : listeners) {
            listener.onPlayerStateChanged(false);
        }
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        applyPlaybackSpeed();
        mp.start();
        for (MusicServiceListener listener : listeners) {
            listener.onPlayerStateChanged(true);
            listener.onPrepared();
        }
    }

    public int getPosn() {
        if (player != null) {
            try {
                return player.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public int getDur() {
        if (player != null) {
            try {
                return player.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public boolean isPng() {
        if (player != null) {
            try {
                return player.isPlaying();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public void pausePlayer() {
        if (player != null) {
            player.pause();
            for (MusicServiceListener listener : listeners) {
                listener.onPlayerStateChanged(false);
            }
        }
    }

    public void seek(int posn) {
        if (player != null) player.seekTo(posn);
    }

    public void go() {
        if (player != null) {
            player.start();
            for (MusicServiceListener listener : listeners) {
                listener.onPlayerStateChanged(true);
            }
        }
    }

    public void playPrev() {
        if (songs == null || songs.isEmpty()) return;
        songPos--;
        if (songPos < 0) songPos = songs.size() - 1;
        playSong();
    }

    public void playNext() {
        if (songs == null || songs.isEmpty()) return;
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
                    // Optionally notify that we reached the end
                    return; 
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

    public int getSongPos() {
        return songPos;
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
                if (player != null) {
                    PlaybackParams params = player.getPlaybackParams();
                    params.setSpeed(playbackSpeed);
                    player.setPlaybackParams(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        listeners.clear();
        super.onDestroy();
    }
}
