package com.coolplayer.music.data

/**
 * 本地媒体扫描工具。
 *
 * 主扫描方式为 MediaStore 查询（由系统 MediaProvider 维护，自动索引所有存储卷，
 * 包括内部存储、SD 卡、U 盘等外置设备），辅以 File 递归遍历补充 MediaStore
 * 尚未索引的文件，结果按 canonicalPath 去重。
 *
 * 支持黑名单：[scanAudios] 接收 [blacklistFolders]，路径前缀匹配的目录会被排除。
 * 支持 [ScanFilters]：格式白名单、最短时长、文件大小范围、自定义扫描根目录、
 * 是否启用 Android 媒体库来源。
 */
object LocalScanner {

    private const val MAX_DEPTH = 8

    /**
     * 扫描本地音频文件。[onFound] 每发现一个文件就回调一次（传入文件名）。
     *
     * @param blacklistFolders 需要排除的文件夹前缀列表（canonical path）
     * @param filters 扫描过滤条件；默认等价于旧行为（全局扫描 + 60 秒以下过滤）
     */
    fun scanAudios(
        context: android.content.Context,
        blacklistFolders: List<String> = emptyList(),
        filters: ScanFilters = ScanFilters(),
        onFound: (String) -> Unit = {}
    ): List<ScannedAudio> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ScannedAudio>()
        val normalizedBlack = blacklistFolders.map { it.trimEnd('/') }
        val allowedExt = filters.allowedExtensions.takeIf { it.isNotEmpty() } ?: musicExtensions

        // 1) MediaStore 查询：覆盖所有存储卷（含 SD 卡 / U 盘），系统维护索引，速度快。
        if (filters.useMediaStore) {
            scanAudiosViaMediaStore(context, seen, out, normalizedBlack, filters, allowedExt, onFound)
        }

        // 2) File 遍历补充：仅在用户明确配置了自定义文件夹时才对这些文件夹做递归遍历
        //    （用于补充 MediaStore 尚未索引到的文件）。
        //    未配置自定义文件夹时（走全局扫描）不再对整个存储卷做 File 遍历兜底——
        //    MediaStore 已经覆盖全局场景，对几千个文件的存储卷做深度 8 的递归
        //    listFiles() 是非常昂贵的 I/O 操作，会导致扫描严重变慢。
        if (filters.customFolders.isNotEmpty()) {
            for (root in filters.customFolders) {
                val dir = java.io.File(root)
                if (!dir.exists()) continue
                runCatching {
                    scanDir(dir, seen, out, normalizedBlack, filters, allowedExt, depth = MAX_DEPTH, onFound = onFound)
                }
            }
        }
        return out
    }

    // ── MediaStore 查询 ─────────────────────────────────────────

    private fun scanAudiosViaMediaStore(
        context: android.content.Context,
        seen: MutableSet<String>,
        out: MutableList<ScannedAudio>,
        blacklist: List<String>,
        filters: ScanFilters,
        allowedExt: Set<String>,
        onFound: (String) -> Unit
    ) {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
        } else {
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.SIZE,
            android.provider.MediaStore.Audio.Media.DATE_MODIFIED,
            android.provider.MediaStore.Audio.Media.DURATION
        )
        // 若配置了自定义文件夹，则只保留这些前缀下的文件（在游标遍历时用路径前缀过滤）。
        val customPrefixes = filters.customFolders.map { it.trimEnd('/') }

        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val dataIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                val nameIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.SIZE)
                val modIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATE_MODIFIED)
                val durIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    if (dataIdx < 0) continue
                    val path = c.getString(dataIdx) ?: continue
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: java.io.File(path).name
                    else java.io.File(path).name
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext.isNotEmpty() && !allowedExt.contains(".$ext")) continue

                    if (customPrefixes.isNotEmpty() && customPrefixes.none { path.startsWith(it) }) continue

                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val modified = if (modIdx >= 0) c.getLong(modIdx) * 1000L else 0L
                    val durationMs = if (durIdx >= 0) c.getLong(durIdx) else 0L
                    val folder = java.io.File(path).parent ?: "/"

                    if (!passesFilters(size, durationMs, filters)) continue

                    val dedupeKey = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
                    if (!seen.add(dedupeKey)) continue
                    if (isBlacklisted(dedupeKey, blacklist)) continue
                    out.add(ScannedAudio(path, name, size, modified, folder, durationMs))
                    onFound(name)
                }
            }
        }
    }

    // ── File 遍历补充 ──────────────────────────────────────────

    private fun scanDir(
        dir: java.io.File,
        seen: MutableSet<String>,
        out: MutableList<ScannedAudio>,
        blacklist: List<String>,
        filters: ScanFilters,
        allowedExt: Set<String>,
        depth: Int,
        onFound: (String) -> Unit
    ) {
        if (depth < 0) return
        val entities = runCatching { dir.listFiles() }.getOrNull() ?: return

        for (entity in entities) {
            val name = entity.name
            if (entity.isDirectory) {
                if (name.startsWith('.') || name == "Android") continue
                val canonDir = runCatching { entity.canonicalPath }.getOrDefault(entity.absolutePath)
                if (isBlacklisted(canonDir, blacklist)) continue
                runCatching {
                    scanDir(entity, seen, out, blacklist, filters, allowedExt, depth - 1, onFound)
                }
            } else if (entity.isFile) {
                val ext = entity.extension.let { if (it.isNotEmpty()) ".$it" else "" }.lowercase()
                val isAudio = allowedExt.contains(ext)
                if (!isAudio) continue

                // 去重键用 canonicalPath
                val dedupeKey = runCatching { entity.canonicalPath }.getOrDefault(entity.absolutePath)
                if (!seen.add(dedupeKey)) continue
                if (isBlacklisted(dedupeKey, blacklist)) continue

                runCatching {
                    val size = entity.length()
                    // 只有开启"过滤短音频"时才需要真实时长；MediaMetadataRetriever 逐文件
                    // 解析音频头开销较大，不需要过滤时不做这个操作，避免拖慢整体扫描速度。
                    val durationMs = if (filters.skipShortAudio) readDurationMs(entity.absolutePath) else 0L
                    if (!passesFilters(size, durationMs, filters)) {
                        seen.remove(dedupeKey)
                        return@runCatching
                    }
                    val modified = entity.lastModified()
                    val folder = entity.parent ?: "/"
                    out.add(ScannedAudio(entity.absolutePath, name, size, modified, folder, durationMs))
                    onFound(name)
                }
            }
        }
    }

    /** 用 MediaMetadataRetriever 兜底读取时长（仅用于 File 遍历补充路径，MediaStore 已自带 DURATION）。 */
    private fun readDurationMs(path: String): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun passesFilters(size: Long, durationMs: Long, filters: ScanFilters): Boolean {
        if (filters.skipShortAudio && durationMs > 0 && durationMs < filters.minDurationSeconds * 1000L) return false
        val sizeMb = size / (1024.0 * 1024.0)
        filters.minSizeMb?.let { if (sizeMb < it) return false }
        filters.maxSizeMb?.let { if (sizeMb > it) return false }
        return true
    }

    private fun isBlacklisted(path: String, blacklist: List<String>): Boolean {
        if (blacklist.isEmpty()) return false
        val p = path.trimEnd('/')
        return blacklist.any { p.startsWith(it) }
    }
}
