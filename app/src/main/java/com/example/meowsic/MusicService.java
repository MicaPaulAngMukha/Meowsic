package com.example.meowsic;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;

import com.example.meowsic.model.Song;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private MediaPlayer player;
    private List<Song> songs = new ArrayList<>();
    private int songPos;
    private final IBinder musicBind = new MusicBinder();
    private float playbackSpeed = 1.0f;
    private boolean isPreparing = false;
    
    private boolean shuffle = false;
    private Random rand;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;
    private int repeatMode = REPEAT_NONE;

    private MediaSessionCompat mediaSession;
    private static final String CHANNEL_ID = "Meowsic_Channel";
    private static final int NOTIFICATION_ID = 1;
    
    public static final String ACTION_PAUSE = "com.example.meowsic.PAUSE";
    public static final String ACTION_PLAY = "com.example.meowsic.PLAY";
    public static final String ACTION_NEXT = "com.example.meowsic.NEXT";
    public static final String ACTION_PREV = "com.example.meowsic.PREV";
    public static final String ACTION_REPEAT = "com.example.meowsic.REPEAT";
    public static final String ACTION_STOP = "com.example.meowsic.STOP";

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

    @Override
    public void onCreate() {
        super.onCreate();
        songPos = 0;
        player = new MediaPlayer();
        rand = new Random();
        
        mediaSession = new MediaSessionCompat(this, "Meowsic");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { go(); }
            @Override
            public void onPause() { pausePlayer(); }
            @Override
            public void onSkipToNext() { playNext(); }
            @Override
            public void onSkipToPrevious() { playPrev(); }
            @Override
            public void onSeekTo(long pos) { seek((int) pos); }
        });
        mediaSession.setActive(true);

        initMusicPlayer();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Meowsic Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Media playback controls");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PLAY: go(); break;
                case ACTION_PAUSE: pausePlayer(); break;
                case ACTION_NEXT: playNext(); break;
                case ACTION_PREV: playPrev(); break;
                case ACTION_REPEAT: 
                    nextRepeatMode();
                    updateNotification();
                    break;
                case ACTION_STOP:
                    stopForeground(true);
                    stopSelf();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    public void setList(List<Song> theSongs) {
        if (theSongs == null) return;
        Song current = getCurrentSong();
        songs = new ArrayList<>(theSongs);
        if (current != null) {
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == current.getId()) {
                    songPos = i;
                    return;
                }
            }
        }
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
        return true;
    }

    public void playSong() {
        if (player == null) return;
        try {
            player.reset();
            isPreparing = false;
            if (songs == null || songs.isEmpty()) return;
            
            if (songPos < 0) songPos = 0;
            if (songPos >= songs.size()) songPos = 0;
            
            Song playSong = songs.get(songPos);
            player.setDataSource(playSong.getPath());
            isPreparing = true;
            player.prepareAsync();
            
            updateMetadata(playSong);
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING);
            
            notifySongChanged(songPos, playSong);
            notifyPlayerStateChanged(false);
            updateNotification();
        } catch (Exception e) {
            isPreparing = false;
            e.printStackTrace();
        }
    }

    private void updateMetadata(Song song) {
        Bitmap albumArt = null;
        try {
            Uri artUri = song.getAlbumArtUri();
            if (artUri != null) {
                InputStream is = getContentResolver().openInputStream(artUri);
                albumArt = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
            }
        } catch (Exception ignored) {}

        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.getAlbum())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getDuration())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build());
    }

    private void updatePlaybackState(int state) {
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (player != null && !isPreparing) {
            try { position = player.getCurrentPosition(); } catch (Exception ignored) {}
        }

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(state, position, playbackSpeed);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateNotification() {
        Song currentSong = getCurrentSong();
        if (currentSong == null) return;

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap albumArt = null;
        try {
            Uri artUri = currentSong.getAlbumArtUri();
            if (artUri != null) {
                InputStream is = getContentResolver().openInputStream(artUri);
                albumArt = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
            }
        } catch (Exception e) {
            albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.meowsic_default_song_album_cover);
        }

        int playPauseIcon = isPng() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseAction = isPng() ? ACTION_PAUSE : ACTION_PLAY;

        int repeatIcon;
        switch (repeatMode) {
            case REPEAT_ONE: repeatIcon = R.drawable.icons8_repeat_one_48; break;
            case REPEAT_ALL: repeatIcon = R.drawable.icons8_repeat_48_on; break;
            default: repeatIcon = R.drawable.icons8_repeat_48_off; break;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icons8_cat_32)
                .setContentTitle(currentSong.getTitle())
                .setContentText(currentSong.getArtist())
                .setLargeIcon(albumArt)
                .setContentIntent(pendingIntent)
                .setOngoing(isPng())
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2, 3))
                .addAction(repeatIcon, "Repeat", getPendingIntent(ACTION_REPEAT))
                .addAction(android.R.drawable.ic_media_previous, "Previous", getPendingIntent(ACTION_PREV))
                .addAction(playPauseIcon, "Play/Pause", getPendingIntent(playPauseAction))
                .addAction(android.R.drawable.ic_media_next, "Next", getPendingIntent(ACTION_NEXT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build());
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    private PendingIntent getPendingIntent(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void setSong(int songIndex) {
        songPos = songIndex;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeatMode == REPEAT_ONE) {
            playSong();
        } else {
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPreparing = false;
        try { mp.reset(); } catch (Exception ignored) {}
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
        notifyPlayerStateChanged(false);
        updateNotification();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPreparing = false;
        try {
            applyPlaybackSpeed();
            mp.start();
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            notifyPlayerStateChanged(true);
            updateNotification();
            for (MusicServiceListener listener : listeners) {
                listener.onPrepared();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getPosn() {
        if (player != null && !isPreparing) {
            try { return player.getCurrentPosition(); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    public int getDur() {
        if (player != null && !isPreparing) {
            try { return player.getDuration(); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    public boolean isPng() {
        if (player != null && !isPreparing) {
            try { return player.isPlaying(); } catch (Exception e) { return false; }
        }
        return false;
    }

    public void pausePlayer() {
        if (player != null && !isPreparing) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    notifyPlayerStateChanged(false);
                    updateNotification();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void seek(int posn) {
        if (player != null && !isPreparing) {
            try {
                player.seekTo(posn);
                updatePlaybackState(isPng() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void go() {
        if (player != null && !isPreparing) {
            try {
                player.start();
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                notifyPlayerStateChanged(true);
                updateNotification();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void playPrev() {
        if (songs == null || songs.isEmpty()) return;
        if (songPos < 0 || songPos >= songs.size()) songPos = 0;
        songPos--;
        if (songPos < 0) songPos = songs.size() - 1;
        playSong();
    }

    public void playNext() {
        if (songs == null || songs.isEmpty()) return;
        if (songPos < 0 || songPos >= songs.size()) songPos = -1;

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
                    pausePlayer();
                    return; 
                }
            }
        }
        playSong();
    }

    private void notifySongChanged(int index, Song song) {
        for (MusicServiceListener listener : listeners) {
            listener.onSongChanged(index, song);
        }
    }

    private void notifyPlayerStateChanged(boolean isPlaying) {
        for (MusicServiceListener listener : listeners) {
            listener.onPlayerStateChanged(isPlaying);
        }
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        applyPlaybackSpeed();
        updatePlaybackState(isPng() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
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
        stopForeground(true);
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        listeners.clear();
        super.onDestroy();
    }
}