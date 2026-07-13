package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {
    // --- Playlist ---
    @Query("SELECT * FROM playlists ORDER BY id DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Int): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // --- Channel ---
    @Query("SELECT * FROM channels ORDER BY channelNo ASC, name ASC")
    fun getAllChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY channelNo ASC, name ASC")
    fun getChannelsByPlaylistId(playlistId: Int): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY channelNo ASC, name ASC")
    fun getFavoriteChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannelById(id: Int): Channel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylistId(playlistId: Int)

    @Update
    suspend fun updateChannel(channel: Channel)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun setFavorite(channelId: Int, isFavorite: Boolean)

    // --- EPG Program ---
    @Query("SELECT * FROM epg_programs WHERE tvgId = :tvgId ORDER BY startTime ASC")
    fun getProgramsForChannel(tvgId: String): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE tvgId = :tvgId ORDER BY startTime ASC")
    suspend fun getProgramsForChannelSync(tvgId: String): List<EpgProgram>

    @Query("SELECT * FROM epg_programs WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime ASC")
    fun getProgramsByTimeRange(startTime: Long, endTime: Long): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE startTime <= :now AND endTime >= :now")
    suspend fun getCurrentProgramsSync(now: Long): List<EpgProgram>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearEpgPrograms()

    // --- History Item ---
    @Query("SELECT * FROM history_items ORDER BY watchedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(historyItem: HistoryItem)

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteHistoryItem(id: Int)

    @Query("DELETE FROM history_items")
    suspend fun clearHistory()
}
