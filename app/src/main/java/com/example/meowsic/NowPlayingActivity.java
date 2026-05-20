package com.example.meowsic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.meowsic.databinding.ActivityNowPlayingBinding;
import com.example.meowsic.model.Song;

import java.util.Locale;

public class NowPlayingActivity extends AppCompatActivity implements MusicService.MusicServiceListener {

    private ActivityNowPlayingBinding binding;
    private MusicService musicService;
    private boolean musicBound = false;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNowPlayingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnPlayPause.setOnClickListener(v -> {
            if (musicBound) {
                if (musicService.isPng()) {
                    musicService.pausePlayer();
                } else {
                    musicService.go();
                }
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            if (musicBound) {
                musicService.playNext();
            }
        });

        binding.btnPrev.setOnClickListener(v -> {
            if (musicBound) {
                musicService.playPrev();
            }
        });

        binding.btnShuffle.setOnClickListener(v -> {
            if (musicBound) {
                musicService.toggleShuffle();
                updateShuffleIcon();
            }
        });

        binding.btnRepeat.setOnClickListener(v -> {
            if (musicBound) {
                musicService.nextRepeatMode();
                updateRepeatIcon();
            }
        });

        binding.btnPlaybackSpeed.setOnClickListener(v -> showPlaybackSpeedPicker());
        binding.btnKebabMenu.setOnClickListener(v -> showKebabMenu());

        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicBound) {
                    musicService.seek(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(updateSeekBarRunnable);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateSeekBar();
            }
        });
    }

    private void showPlaybackSpeedPicker() {
        final String[] speeds = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
        final float[] speedValues = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Playback Speed");
        builder.setItems(speeds, (dialog, which) -> {
            if (musicBound) {
                musicService.setPlaybackSpeed(speedValues[which]);
                Toast.makeText(this, "Speed set to " + speeds[which], Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showKebabMenu() {
        final String[] options = {"Edit", "Add to Playlist", "Add to Album", "Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(options, (dialog, which) -> {
            Toast.makeText(this, "Selected: " + options[which], Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void updateUI() {
        if (musicBound) {
            Song currentSong = musicService.getCurrentSong();
            if (currentSong != null) {
                binding.tvSongName.setText(currentSong.getTitle());
                String artist = currentSong.getArtist();
                binding.tvArtistName.setText(artist != null && !artist.equals("<unknown>") ? artist : "Unknown Artist");
                
                Glide.with(this)
                    .load(currentSong.getAlbumArtUri())
                    .placeholder(R.drawable.meowsic_default_song_album_cover)
                    .error(R.drawable.meowsic_default_song_album_cover)
                    .into(binding.ivAlbumArt);

                if (musicService.isPng()) {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                }

                updateShuffleIcon();
                updateRepeatIcon();

                binding.seekBar.setMax(musicService.getDur());
                updateSeekBar();
            }
        }
    }

    private void updateShuffleIcon() {
        if (musicBound && musicService.isShuffle()) {
            binding.btnShuffle.setImageResource(R.drawable.icons8_shuffle_30_on);
        } else {
            binding.btnShuffle.setImageResource(R.drawable.icons8_shuffle_30);
        }
    }

    private void updateRepeatIcon() {
        if (!musicBound) return;
        switch (musicService.getRepeatMode()) {
            case MusicService.REPEAT_NONE:
                binding.btnRepeat.setImageResource(R.drawable.icons8_repeat_48_off);
                break;
            case MusicService.REPEAT_ONE:
                binding.btnRepeat.setImageResource(R.drawable.icons8_repeat_one_48);
                break;
            case MusicService.REPEAT_ALL:
                binding.btnRepeat.setImageResource(R.drawable.icons8_repeat_48_on);
                break;
        }
    }

    private void updateSeekBar() {
        if (musicBound) {
            binding.seekBar.setProgress(musicService.getPosn());
            if (musicService.isPng()) {
                handler.postDelayed(updateSeekBarRunnable, 1000);
            }
        }
    }

    private Runnable updateSeekBarRunnable = this::updateSeekBar;

    private ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.addListener(NowPlayingActivity.this);
            musicBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, musicConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (musicBound) {
            musicService.removeListener(this);
            unbindService(musicConnection);
            musicBound = false;
        }
        handler.removeCallbacks(updateSeekBarRunnable);
    }

    @Override
    public void onSongChanged(int songIndex, Song song) {
        runOnUiThread(this::updateUI);
    }

    @Override
    public void onPlayerStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            if (isPlaying) {
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                updateSeekBar();
            } else {
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                handler.removeCallbacks(updateSeekBarRunnable);
            }
        });
    }

    @Override
    public void onPrepared() {
        runOnUiThread(() -> {
            binding.seekBar.setMax(musicService.getDur());
            updateSeekBar();
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_down);
    }
}