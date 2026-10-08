package com.example.audiary.data

import com.example.audiary.model.Song

interface MusicRepository {
    suspend fun getSongs(): List<Song>
    suspend fun getSong(id: String): Song?
}
