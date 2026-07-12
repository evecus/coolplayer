package com.coolplayer.music.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── 实体 ───────────────────────────────────────────────────────────────

/**
 * 收藏歌曲（按 path 主键去重）。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val name: String,
    val folder: String,
    val size: Long,
    val modified: Long,
    val coverPath: String,
    val lyrics: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 自建歌单元数据。
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 歌单 - 歌曲 关联表（多对多，按 orderInPlaylist 排序）。
 */
@Entity(
    tableName = "playlist_song",
    primaryKeys = ["playlistId", "path"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("path")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val name: String,
    val folder: String,
    val size: Long,
    val modified: Long,
    val coverPath: String,
    val lyrics: String,
    val orderInPlaylist: Int
)

/**
 * 播放历史（按 path 去重，每次播放更新 playedAt）。
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val name: String,
    val folder: String,
    val size: Long,
    val modified: Long,
    val coverPath: String,
    val lyrics: String,
    val playedAt: Long = System.currentTimeMillis()
)

// ── DAO ─────────────────────────────────────────────────────────────────

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    fun isFavoriteFlow(path: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun delete(path: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_song WHERE playlistId = :playlistId ORDER BY orderInPlaylist ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_song WHERE playlistId = :playlistId ORDER BY orderInPlaylist ASC")
    suspend fun getSongs(playlistId: Long): List<PlaylistSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_song WHERE playlistId = :playlistId AND path = :path")
    suspend fun removeSong(playlistId: Long, path: String)

    @Query("UPDATE playlist_song SET orderInPlaylist = :order WHERE playlistId = :playlistId AND path = :path")
    suspend fun updateOrder(playlistId: Long, path: String, order: Int)

    @Query("SELECT COUNT(*) FROM playlist_song WHERE playlistId = :playlistId")
    suspend fun count(playlistId: Long): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE path = :path")
    suspend fun delete(path: String)
}

// ── Database ────────────────────────────────────────────────────────────

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salt_music.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// ── Repository ──────────────────────────────────────────────────────────

/**
 * 歌单 / 收藏 / 历史统一仓储。
 */
class MusicRepository(private val db: AppDatabase) {
    private val favoriteDao = db.favoriteDao()
    private val playlistDao = db.playlistDao()
    private val historyDao = db.historyDao()

    // ── 收藏 ──────────────────────────────────────────
    fun observeFavorites() = favoriteDao.observeAll()
    fun isFavoriteFlow(path: String) = favoriteDao.isFavoriteFlow(path)

    suspend fun toggleFavorite(song: SongEntry): Boolean {
        val now = favoriteDao.isFavorite(song.path)
        return if (now) {
            favoriteDao.delete(song.path)
            false
        } else {
            favoriteDao.insert(song.toFavoriteEntity())
            true
        }
    }

    suspend fun isFavorite(path: String) = favoriteDao.isFavorite(path)

    // ── 歌单 ──────────────────────────────────────────
    fun observePlaylists() = playlistDao.observePlaylists()
    fun observePlaylistSongs(id: Long) = playlistDao.observeSongs(id)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun renamePlaylist(id: Long, name: String) {
        val p = playlistDao.getPlaylist(id) ?: return
        playlistDao.updatePlaylist(p.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(id: Long) = playlistDao.deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, song: SongEntry) {
        val count = playlistDao.count(playlistId)
        playlistDao.insertSong(song.toPlaylistSongEntity(playlistId, count))
        val p = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(p.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun removeFromPlaylist(playlistId: Long, path: String) {
        playlistDao.removeSong(playlistId, path)
    }

    suspend fun getPlaylistSongs(playlistId: Long) = playlistDao.getSongs(playlistId)

    // ── 播放历史 ──────────────────────────────────────
    fun observeRecent(limit: Int = 200) = historyDao.observeRecent(limit)

    suspend fun recordPlay(song: SongEntry) {
        historyDao.upsert(song.toHistoryEntity())
    }

    suspend fun clearHistory() = historyDao.clear()
}

// ── 转换辅助 ─────────────────────────────────────────────────────────────

private fun SongEntry.toFavoriteEntity() = FavoriteEntity(
    path = path, title = title, artist = artist, album = album,
    name = name, folder = folder, size = size, modified = modified,
    coverPath = coverPath, lyrics = lyrics
)

private fun SongEntry.toPlaylistSongEntity(playlistId: Long, order: Int) = PlaylistSongEntity(
    playlistId = playlistId, path = path, title = title, artist = artist, album = album,
    name = name, folder = folder, size = size, modified = modified,
    coverPath = coverPath, lyrics = lyrics, orderInPlaylist = order
)

private fun SongEntry.toHistoryEntity() = HistoryEntity(
    path = path, title = title, artist = artist, album = album,
    name = name, folder = folder, size = size, modified = modified,
    coverPath = coverPath, lyrics = lyrics
)

/** 将 Room 实体转回 SongEntry（用于播放列表构造）。 */
fun FavoriteEntity.toSongEntry() = SongEntry(path, name, folder, size, modified).apply {
    title = this@toSongEntry.title
    artist = this@toSongEntry.artist
    album = this@toSongEntry.album
    coverPath = this@toSongEntry.coverPath
    lyrics = this@toSongEntry.lyrics
    metadataLoaded = true
}

fun PlaylistSongEntity.toSongEntry() = SongEntry(path, name, folder, size, modified).apply {
    title = this@toSongEntry.title
    artist = this@toSongEntry.artist
    album = this@toSongEntry.album
    coverPath = this@toSongEntry.coverPath
    lyrics = this@toSongEntry.lyrics
    metadataLoaded = true
}

fun HistoryEntity.toSongEntry() = SongEntry(path, name, folder, size, modified).apply {
    title = this@toSongEntry.title
    artist = this@toSongEntry.artist
    album = this@toSongEntry.album
    coverPath = this@toSongEntry.coverPath
    lyrics = this@toSongEntry.lyrics
    metadataLoaded = true
}
