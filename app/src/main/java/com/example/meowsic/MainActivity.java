package com.example.meowsic;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.meowsic.databinding.ActivityMainBinding;
import com.example.meowsic.model.Song;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MusicService.MusicServiceListener {

    private ActivityMainBinding binding;
    private MusicService musicService;
    private Intent playIntent;
    private boolean musicBound = false;
    private MusicViewModel viewModel;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply system bar insets to avoid clashing with status bar and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(MusicViewModel.class);

        checkPermissions();
        setupUI();
        observeViewModel();
    }

    private void checkPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
        } else {
            scanForMusic();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanForMusic();
            } else {
                Toast.makeText(this, "Permission Denied. Cannot load music.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void scanForMusic() {
        List<Song> songList = new ArrayList<>();
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor musicCursor = musicResolver.query(musicUri, null, selection, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int albumIdColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int dataColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int durationColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                if (thisArtist == null || thisArtist.equals("<unknown>")) thisArtist = "Unknown Artist";
                
                String thisAlbum = musicCursor.getString(albumColumn);
                String thisPath = musicCursor.getString(dataColumn);
                long thisDuration = musicCursor.getLong(durationColumn);
                
                long albumId = musicCursor.getLong(albumIdColumn);
                Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
                Uri albumArtUri = Uri.withAppendedPath(sArtworkUri, String.valueOf(albumId));
                
                songList.add(new Song(thisId, thisTitle, thisArtist, thisAlbum, thisDuration, thisPath, albumArtUri));
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
        
        viewModel.setSongs(songList);
        if (musicService != null) {
            musicService.setList(songList);
        }
    }

    private void setupUI() {
        binding.btnSearch.setOnClickListener(v -> {
            if (binding.etSearch.getVisibility() == View.VISIBLE) {
                binding.etSearch.setVisibility(View.GONE);
                binding.searchSpacer.setVisibility(View.VISIBLE);
                binding.etSearch.setText("");
            } else {
                binding.etSearch.setVisibility(View.VISIBLE);
                binding.searchSpacer.setVisibility(View.GONE);
                binding.etSearch.requestFocus();
            }
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Songs"); break;
                case 1: tab.setText("Playlist"); break;
                case 2: tab.setText("Albums"); break;
                case 3: tab.setText("Artist"); break;
            }
        }).attach();

        binding.miniPlayer.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(this, NowPlayingActivity.class);
            Bundle bundle = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.slide_up, R.anim.stay).toBundle();
            startActivity(intent, bundle);
        });

        binding.miniPlayer.btnMiniPlayPause.setOnClickListener(v -> {
            if (musicBound) {
                if (musicService.isPng()) {
                    musicService.pausePlayer();
                } else {
                    musicService.go();
                }
            }
        });

        binding.miniPlayer.btnMiniNext.setOnClickListener(v -> {
            if (musicBound) musicService.playNext();
        });
    }

    private void observeViewModel() {
        viewModel.getCurrentSongIndex().observe(this, index -> {
            if (index != null && viewModel.getSongs().getValue() != null && index >= 0 && index < viewModel.getSongs().getValue().size()) {
                Song currentSong = viewModel.getSongs().getValue().get(index);
                binding.miniPlayer.tvMiniSongName.setText(currentSong.getTitle());
                binding.miniPlayer.tvMiniArtist.setText(currentSong.getArtist());
                
                Glide.with(this)
                    .load(currentSong.getAlbumArtUri())
                    .placeholder(R.drawable.meowsic_default_song_album_cover)
                    .error(R.drawable.meowsic_default_song_album_cover)
                    .into(binding.miniPlayer.ivMiniAlbumArt);

                binding.miniPlayer.getRoot().setVisibility(View.VISIBLE);
            }
        });

        viewModel.isPlaying().observe(this, isPlaying -> {
            if (isPlaying) {
                binding.miniPlayer.btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                binding.miniPlayer.btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
        });
    }

    private ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.setListener(MainActivity.this);
            if (viewModel.getSongs().getValue() != null) {
                musicService.setList(viewModel.getSongs().getValue());
            }
            musicBound = true;
            
            // Sync UI if already playing
            if (musicService.isPng() || musicService.getCurrentSong() != null) {
                viewModel.setCurrentSongIndex(musicService.getSongPos());
                viewModel.setPlaying(musicService.isPng());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    public void playSong(int index) {
        if (musicBound) {
            musicService.setSong(index);
            musicService.playSong();
        }
    }

    @Override
    public void onSongChanged(int songIndex, Song song) {
        viewModel.setCurrentSongIndex(songIndex);
    }

    @Override
    public void onPlayerStateChanged(boolean isPlaying) {
        viewModel.setPlaying(isPlaying);
    }

    @Override
    public void onPrepared() {
        viewModel.setPlaying(true);
    }

    @Override
    protected void onDestroy() {
        if (musicBound) {
            musicService.setListener(null);
            unbindService(musicConnection);
        }
        if (playIntent != null) stopService(playIntent);
        musicService = null;
        super.onDestroy();
    }
}
