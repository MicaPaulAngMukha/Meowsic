package com.example.meowsic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.meowsic.model.Song;
import java.util.List;
import java.util.Locale;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private List<Song> songs;
    private OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    public SongAdapter(List<Song> songs, OnSongClickListener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());
        holder.tvDuration.setText(formatDuration(song.getDuration()));
        holder.itemView.setOnClickListener(v -> listener.onSongClick(position));
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    private String formatDuration(long duration) {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvArtist, tvDuration;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvSongArtist);
            tvDuration = itemView.findViewById(R.id.tvSongDuration);
        }
    }
}