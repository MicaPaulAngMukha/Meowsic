package com.example.meowsic;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new SongsFragment();
            case 1: return new PlaylistFragment();
            case 2: return new AlbumsFragment();
            case 3: return new ArtistFragment();
            default: return new SongsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}