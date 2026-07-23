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

/**
 * 完整音乐库缓存（原来存在 SharedPreferences + JSON，现迁移进 Room）。
 *
 * 每次 [com.coolplayer.music.ui.library.LibraryViewModel.rescan] 完成后整表替换写入。
 * [playCount] 直接落在本表里（原来是单独一份 path -> count 的 JSON 映射，
 * 现在合并进来，每次播放用一条 UPDATE 自增，不再需要整表重写）。
 *
 * 索引：title/artist/album/folder/modified 均建索引，配合 SQL ORDER BY / LIKE 做排序与搜索，
 * 避免把全量数据读到内存里用 Kotlin sortedWith / filter。
 */
@Entity(
    tableName = "song_library",
    indices = [
        Index("title"),
        Index("artist"),
        Index("album"),
        Index("folder"),
        Index("modified"),
        Index("size"),
        Index("durationMs"),
        Index("playCount")
    ]
)
data class SongLibraryEntity(
    @PrimaryKey val path: String,
    val name: String,
    val folder: String,
    val size: Long,
    val modified: Long,
    val durationMs: Long,
    val title: String,
    val artist: String,
    val album: String,
    val coverPath: String,
    val lyrics: String,
    val playCount: Int = 0
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

/**
 * 音乐库 DAO：整表替换式写入 + 基于 SQL 的排序/搜索/分组统计查询。
 *
 * 排序：8 个字段各配一条 ASC/DESC 查询（Room 不支持把 ORDER BY 列名当参数绑定，
 * 只能枚举），关键字搜索用 LIKE，均在数据库层完成，避免把全表读到内存里
 * 再用 Kotlin sortedWith/filter（那样内存占用和 CPU 开销都会随歌曲数线性增长）。
 */
@Dao
interface SongLibraryDao {

    // ── 观察 / 读取全量（供分组页 专辑/艺术家/文件夹 聚合使用） ────────────
    @Query("SELECT * FROM song_library")
    fun observeAll(): Flow<List<SongLibraryEntity>>

    @Query("SELECT * FROM song_library")
    suspend fun getAll(): List<SongLibraryEntity>

    @Query("SELECT COUNT(*) FROM song_library")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM song_library WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): SongLibraryEntity?

    // ── 整表替换（扫描完成后调用） ───────────────────────────────────────
    @Transaction
    suspend fun replaceAll(songs: List<SongLibraryEntity>) {
        clearAll()
        insertAll(songs)
    }

    @Query("DELETE FROM song_library")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongLibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongLibraryEntity)

    @Query("DELETE FROM song_library WHERE path = :path")
    suspend fun delete(path: String)

    // ── 播放次数：单条 UPDATE 自增，不重写整张表 ──────────────────────────
    @Query("UPDATE song_library SET playCount = playCount + 1 WHERE path = :path")
    suspend fun incrementPlayCount(path: String)

    @Query("SELECT playCount FROM song_library WHERE path = :path")
    suspend fun getPlayCount(path: String): Int?

    // ── 排序查询（无关键字）：按字段 + 方向枚举 ───────────────────────────
    @Query("SELECT * FROM song_library ORDER BY title COLLATE NOCASE ASC")
    suspend fun sortByTitleAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY title COLLATE NOCASE DESC")
    suspend fun sortByTitleDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY modified ASC")
    suspend fun sortByModifiedAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY modified DESC")
    suspend fun sortByModifiedDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY name COLLATE NOCASE ASC")
    suspend fun sortByNameAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY name COLLATE NOCASE DESC")
    suspend fun sortByNameDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY album COLLATE NOCASE ASC")
    suspend fun sortByAlbumAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY album COLLATE NOCASE DESC")
    suspend fun sortByAlbumDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    suspend fun sortByArtistAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY artist COLLATE NOCASE DESC, title COLLATE NOCASE ASC")
    suspend fun sortByArtistDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY size ASC")
    suspend fun sortBySizeAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY size DESC")
    suspend fun sortBySizeDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY durationMs ASC")
    suspend fun sortByDurationAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY durationMs DESC")
    suspend fun sortByDurationDesc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY playCount ASC")
    suspend fun sortByPlayCountAsc(): List<SongLibraryEntity>

    @Query("SELECT * FROM song_library ORDER BY playCount DESC")
    suspend fun sortByPlayCountDesc(): List<SongLibraryEntity>

    // ── 排序查询（带关键字搜索，标题/艺术家/专辑模糊匹配） ─────────────────
    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY title COLLATE NOCASE ASC"
    )
    suspend fun searchByTitleAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY title COLLATE NOCASE DESC"
    )
    suspend fun searchByTitleDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY modified ASC"
    )
    suspend fun searchByModifiedAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY modified DESC"
    )
    suspend fun searchByModifiedDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY name COLLATE NOCASE ASC"
    )
    suspend fun searchByNameAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY name COLLATE NOCASE DESC"
    )
    suspend fun searchByNameDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY album COLLATE NOCASE ASC"
    )
    suspend fun searchByAlbumAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY album COLLATE NOCASE DESC"
    )
    suspend fun searchByAlbumDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
    )
    suspend fun searchByArtistAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY artist COLLATE NOCASE DESC, title COLLATE NOCASE ASC"
    )
    suspend fun searchByArtistDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY size ASC"
    )
    suspend fun searchBySizeAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY size DESC"
    )
    suspend fun searchBySizeDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY durationMs ASC"
    )
    suspend fun searchByDurationAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY durationMs DESC"
    )
    suspend fun searchByDurationDesc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY playCount ASC"
    )
    suspend fun searchByPlayCountAsc(kw: String): List<SongLibraryEntity>

    @Query(
        "SELECT * FROM song_library WHERE title LIKE '%' || :kw || '%' " +
            "OR artist LIKE '%' || :kw || '%' OR album LIKE '%' || :kw || '%' " +
            "ORDER BY playCount DESC"
    )
    suspend fun searchByPlayCountDesc(kw: String): List<SongLibraryEntity>

    // ── 分组聚合：专辑 / 文件夹（艺术家因需要拆分多值字段，仍在内存里用 ArtistSplitter 处理） ──
    @Query(
        "SELECT CASE WHEN album = '' THEN '未知专辑' ELSE album END AS groupKey, " +
            "COUNT(*) AS songCount, MAX(modified) AS latestModified " +
            "FROM song_library GROUP BY groupKey"
    )
    suspend fun groupByAlbum(): List<GroupStat>

    @Query(
        "SELECT folder AS groupKey, COUNT(*) AS songCount, MAX(modified) AS latestModified " +
            "FROM song_library GROUP BY folder"
    )
    suspend fun groupByFolder(): List<GroupStat>
}

/** 分组统计投影（专辑 / 文件夹分组页的轻量计数，不加载歌曲本身）。 */
data class GroupStat(
    val groupKey: String,
    val songCount: Int,
    val latestModified: Long
)

// ── Database ────────────────────────────────────────────────────────────

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class,
        SongLibraryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun songLibraryDao(): SongLibraryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2：新增 song_library 表（完整音乐库缓存，原来存在 SharedPreferences JSON 里）。 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `song_library` (
                        `path` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `folder` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `modified` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `coverPath` TEXT NOT NULL,
                        `lyrics` TEXT NOT NULL,
                        `playCount` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_title` ON `song_library` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_artist` ON `song_library` (`artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_album` ON `song_library` (`album`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_folder` ON `song_library` (`folder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_modified` ON `song_library` (`modified`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_size` ON `song_library` (`size`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_durationMs` ON `song_library` (`durationMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_library_playCount` ON `song_library` (`playCount`)")
            }
        }

        fun get(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salt_music.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
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
    val songLibraryDao = db.songLibraryDao()

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

// ── 音乐库缓存转换辅助 ───────────────────────────────────────────────────

fun SongEntry.toLibraryEntity() = SongLibraryEntity(
    path = path, name = name, folder = folder, size = size, modified = modified,
    durationMs = durationMs, title = title, artist = artist, album = album,
    coverPath = coverPath, lyrics = lyrics, playCount = playCount
)

fun SongLibraryEntity.toSongEntry() = SongEntry(path, name, folder, size, modified, durationMs).apply {
    title = this@toSongEntry.title
    artist = this@toSongEntry.artist
    album = this@toSongEntry.album
    coverPath = this@toSongEntry.coverPath
    lyrics = this@toSongEntry.lyrics
    playCount = this@toSongEntry.playCount
    metadataLoaded = true
}
