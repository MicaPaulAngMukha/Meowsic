package com.example.meowsic;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.meowsic.databinding.FragmentSongsBinding;
import com.example.meowsic.model.Song;
import java.util.ArrayList;
import java.util.List;

public class SongsFragment extends Fragment {

    private FragmentSongsBinding binding;
    private MusicViewModel viewModel;
    private SongAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSongsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        
        setupRecyclerView();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new SongAdapter(new ArrayList<>(), position -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).playSong(position);
            }
        });
        binding.rvSongs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSongs.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getSongs().observe(getViewLifecycleOwner(), songs -> {
            if (songs != null && !songs.isEmpty()) {
                adapter = new SongAdapter(songs, position -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).playSong(position);
                    }
                });
                binding.rvSongs.setAdapter(adapter);
                binding.tvNoSongs.setVisibility(View.GONE);
            } else {
                binding.tvNoSongs.setVisibility(View.VISIBLE);
            }
        });
    }
}