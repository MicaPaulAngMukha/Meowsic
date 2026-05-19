package com.example.meowsic.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "albums")
public class Album {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;

    public Album(String name) {
        this.name = name;
    }
}