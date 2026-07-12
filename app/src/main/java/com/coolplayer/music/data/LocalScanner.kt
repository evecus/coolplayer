package com.coolplayer.music.data

/**
 * 本地媒体扫描工具。
 *
 * 主扫描方式为 MediaStore 查询（由系统 MediaProvider 维护，自动索引所有存储卷，
 * 包括内部存储、SD 卡、U 盘等外置设备），辅以 File 递归遍历补充 MediaStore
 * 尚未索引的文件，结果按 canonicalPath 去重。
 *
 * 支持黑名单：[scanAudios] 接收 [blacklistFolders]，路径前缀匹配的目录会被排除。
 */
object LocalScanner {

    private const val MAX_DEPTH = 8

    /**
     * 扫描本地音频文件。[onFound] 每发现一个文件就回调一次。
     *
     * @param blacklistFolders 需要排除的文件夹前缀列表（canonical path）
     */
    fun scanAudios(
        context: android.content.Context,
        blacklistFolders: List<String> = emptyList(),
        onFound: (String) -> Unit = {}
    ): List<ScannedAudio> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ScannedAudio>()
        val normalizedBlack = blacklistFolders.map { it.trimEnd('/') }
        // 1) MediaStore 查询：覆盖所有存储卷（含 SD 卡 / U 盘）
        scanAudiosViaMediaStore(context, seen, out, normalizedBlack, onFound)
        // 2) File 遍历补充：扫到 MediaStore 尚未索引的文件
        for (root in discoverRoots(context)) {
            val dir = java.io.File(root)
            if (!dir.exists()) continue
            runCatching {
                scanDir(dir, seen, out, normalizedBlack, depth = MAX_DEPTH, onFound = onFound)
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
            android.provider.MediaStore.Audio.Media.DATE_MODIFIED
        )
        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val dataIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                val nameIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.SIZE)
                val modIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATE_MODIFIED)
                while (c.moveToNext()) {
                    if (dataIdx < 0) continue
                    val path = c.getString(dataIdx) ?: continue
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: java.io.File(path).name
                    else java.io.File(path).name
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext.isNotEmpty() && !musicExtensions.contains(".$ext")) continue
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val modified = if (modIdx >= 0) c.getLong(modIdx) * 1000L else 0L
                    val folder = java.io.File(path).parent ?: "/"
                    val dedupeKey = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
                    if (!seen.add(dedupeKey)) continue
                    if (isBlacklisted(dedupeKey, blacklist)) continue
                    out.add(ScannedAudio(path, name, size, modified, folder))
                    onFound(name)
                }
            }
        }
    }

    // ── File 遍历补充 ──────────────────────────────────────────

    private fun discoverRoots(context: android.content.Context): List<String> {
        val roots = mutableSetOf<String>()

        // 1) getExternalFilesDirs(null) 为每个挂载的外部存储卷返回一个本应用专属目录
        runCatching {
            context.getExternalFilesDirs(null).forEach { dir ->
                if (dir != null) {
                    volumeRootFromAppDir(dir.absolutePath)?.let { roots.add(it) }
                }
            }
        }

        // 2) 枚举 /storage 下的条目作为补充
        runCatching {
            val storageDir = java.io.File("/storage")
            if (storageDir.exists()) {
                storageDir.listFiles()?.forEach { f ->
                    val name = f.name
                    if (name != "self" && name != "emulated" && f.isDirectory) {
                        roots.add(f.absolutePath)
                    }
                }
            }
        }

        // 3) 兜底经典内部存储路径
        roots.add("/storage/emulated/0")
        roots.add("/sdcard")

        // 用 canonicalPath 去重（解析符号链接，如 /sdcard -> /storage/emulated/0）
        val resolved = mutableMapOf<String, String>()
        for (root in roots) {
            runCatching {
                val dir = java.io.File(root)
                if (dir.exists()) {
                    val real = dir.canonicalPath
                    resolved.putIfAbsent(real, root)
                }
            }
        }
        return resolved.values.toList()
    }

    private fun volumeRootFromAppDir(appDirPath: String): String? {
        val normalized = appDirPath.replace('\\', '/')
        val idx = normalized.indexOf("/Android/")
        if (idx <= 0) return null
        return normalized.substring(0, idx)
    }

    private fun scanDir(
        dir: java.io.File,
        seen: MutableSet<String>,
        out: MutableList<ScannedAudio>,
        blacklist: List<String>,
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
                    scanDir(entity, seen, out, blacklist, depth - 1, onFound)
                }
            } else if (entity.isFile) {
                val ext = entity.extension.let { if (it.isNotEmpty()) ".$it" else "" }.lowercase()
                val isAudio = musicExtensions.contains(ext)
                if (!isAudio) continue

                // 去重键用 canonicalPath
                val dedupeKey = runCatching { entity.canonicalPath }.getOrDefault(entity.absolutePath)
                if (!seen.add(dedupeKey)) continue
                if (isBlacklisted(dedupeKey, blacklist)) continue

                runCatching {
                    val size = entity.length()
                    val modified = entity.lastModified()
                    val folder = entity.parent ?: "/"
                    out.add(ScannedAudio(entity.absolutePath, name, size, modified, folder))
                    onFound(name)
                }
            }
        }
    }

    private fun isBlacklisted(path: String, blacklist: List<String>): Boolean {
        if (blacklist.isEmpty()) return false
        val p = path.trimEnd('/')
        return blacklist.any { p.startsWith(it) }
    }
}
