package com.example.meowsic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.meowsic.model.Song;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MusicViewModel extends ViewModel {
    private final MutableLiveData<List<Song>> allSongs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<Song>> filteredSongs = new MediatorLiveData<>();
    
    private final MutableLiveData<Song> currentSong = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);

    public MusicViewModel() {
        filteredSongs.addSource(allSongs, songs -> applyFilter());
        filteredSongs.addSource(searchQuery, query -> applyFilter());
    }

    private void applyFilter() {
        List<Song> songs = allSongs.getValue();
        String query = searchQuery.getValue();
        
        if (songs == null) {
            filteredSongs.setValue(new ArrayList<>());
            return;
        }
        
        if (query == null || query.isEmpty()) {
            filteredSongs.setValue(songs);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            List<Song> filtered = songs.stream()
                    .filter(song -> (song.getTitle() != null && song.getTitle().toLowerCase().contains(lowerCaseQuery)) ||
                                   (song.getArtist() != null && song.getArtist().toLowerCase().contains(lowerCaseQuery)))
                    .collect(Collectors.toList());
            filteredSongs.setValue(filtered);
        }
    }

    public void setSongs(List<Song> songs) {
        allSongs.setValue(songs);
    }

    public LiveData<List<Song>> getSongs() {
        return filteredSongs;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public void setCurrentSong(Song song) {
        currentSong.postValue(song);
    }

    public LiveData<Song> getCurrentSong() {
        return currentSong;
    }

    public void setPlaying(boolean playing) {
        isPlaying.postValue(playing);
    }

    public LiveData<Boolean> isPlaying() {
        return isPlaying;
    }
}