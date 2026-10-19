# coolplayer-optimized 改动清单

本项目由 coolplayer-master 复制并实施以下改动，全部改动已通过
**Kotlin 2.0.20 全项目真实编译验证**（kotlinc + Compose 编译器插件 +
API 34 android.jar + 项目真实依赖栈），0 error。

---

## 一、播放 CPU 占用优化（对齐 Salt Player 的 BASS 引擎实现策略）

### 1. 进度轮询降频 — `player/MusicPlayer.kt`
- 播放进度轮询 `delay(200L)` → `delay(500L)`，Compose 重组频率下降 60%。
- Salt 的 BASS 实现同样用低频轮询 + 事件驱动（`BASS_SYNC_POS`）而非高频轮询。

### 2. 歌词视图虚拟化 — `ui/player/LrcView.kt`（整体重写）
- 原实现：自定义 `Layout` 对**全部歌词行**做 measure/draw，百行歌词每帧
  全量计算；`targetOffset` 手动计算滚动偏移。
- 现实现：`BoxWithConstraints` 取视口高度 + `LazyColumn` 虚拟化（仅可见行
  参与测量与绘制），`LaunchedEffect(currentIdx) { animateScrollToItem }`
  平滑居中。百行歌词的每帧开销从 O(N) 降为 O(可见行数)。

### 3. position 重组局部化 — `ui/player/PlayerScreen.kt`
- 原实现：PlayerScreen 顶层 `collectAsState()` position，每 200ms 整页重组。
- 现实现：顶层只传 `positionFlow: StateFlow<Long>`；`ProgressBar` 与
  `LyricsPane` 各自在内部收集，重组范围限制在进度条/歌词面板局部。
- LyricsPane 内部 `LaunchedEffect(state) { positionFlow.collect { state.updateProgress(it) } }`，
  仅 currentIndex 切换时才触发歌词列表重组。

### 4. 元数据读取缓存化 — `player/MusicPlayer.kt` + `player/CoverCache.kt`（新文件）
- `loadMetadata`：`song.metadataLoaded` 为 true 时完全跳过 JAudioTagger
  全量解析（音频头 + 标签 + 封面提取，旗舰机上也常需数十毫秒 CPU），
  直接复用 Room 字段。
- 封面：新增 `CoverCache`（`LruCache<String, ByteArray>`，16MB），播放页
  封面字节跨切歌复用；`AudioMetadata.readCoverOnly(filePath)` 只读内嵌
  封面，不再走全量标签解析。
- `restoreLastSession`：原实现对播放列表 N 个文件**串行**调用
  JAudioTagger 全量解析（Service 每次重建都触发，是启动期 CPU/IO 峰值
  主要来源）；现改为 Room `getByPaths(paths)` 一次 IN 查询拿回全部元数据
  （`data/Database.kt` 新增该 DAO 方法 + `SongLibraryEntity.toSongEntry()`
  映射已存在，补上 MusicPlayer 侧 import）。
- 数据库查不到的路径退化为仅含 File 基本信息的条目（metadataLoaded=false），
  后续 `loadMetadata` 按需补一次读盘，行为自愈。

### 5. 淡入淡出 — `player/MusicPlayer.kt` + `data/StorageService.kt` + `ui/settings/SettingsScreen.kt`
- 移植 Salt 的 `fade_in_or_out_during_play_or_pause`：播放/暂停时 300ms /
  15 步音量斜坡，消除爆音；设置页新增"淡入淡出"开关（`kFadeInOut`）。
- `onIsPlayingChanged` 自愈：外部（通知栏/蓝牙）触发播放且音量未满时
  自动补淡入，避免"在放没声音"。

### 6. 移除死重量依赖 — `app/build.gradle.kts`
- 移除 `media3-exoplayer-hls`：本项目只播放本地音频文件，没有任何
  HLS(m3u8) 网络流播放入口，该模块约 1.5 万方法数，纯死重量
  （影响 R8 后体积与启动期类验证/编译开销）。
- 移除 `io.coil-kt:coil-compose` 之外的未使用 Glide 依赖（全项目无引用，
  widget 封面走 BitmapDecodeUtil、Compose 内走 Coil）。

---

## 二、AutoEQ 校正库移植（8850 款耳机）

### 新增文件
| 文件 | 说明 |
|---|---|
| `app/src/main/assets/audiofx/autoeq_catalog.tsv` | AutoEQ 全量 catalog，8850 条校正曲线（2.1MB），格式 `hash \t type \t b64(source) \t b64(name) \t preamp \t bands`，每条 = 1 低频架 + 8 钟形 + 1 高频架 |
| `app/src/main/assets/audiofx/autoeq_catalog_LICENSE.txt` | MIT 许可全文 + AutoEq 项目署名 + oratory1990/crinacle/Rtings 等测量原作者署名 |
| `player/audiofx/AutoEqCatalog.kt` | catalog 解析器：懒加载预解码索引（8850 条常驻约 4MB），按需解析单条为 EqPreset，大小写不敏感搜索（IO 线程加载、Default 线程搜索） |
| `player/audiofx/EqPresetIO.kt` | EQ 预设导入/导出 + 自定义预设持久化 |

### UI — `ui/audiofx/AudioFxScreen.kt`
- 新增"AutoEQ 校正库"搜索区：搜索框（型号/作者，如 "Sony WH-1000XM5"），
  结果列表展示佩戴类型（入耳式/头戴式/耳塞）与测量作者，点击即应用。
- 手机/平板双布局均接入；原"热门耳机（4 款）"快速入口保留。

### DSP 扩展 — `player/audiofx/BiquadFilter.kt` + `EqModels.kt`
- `FilterType` 新增 `LPF`/`HPF`，实现 RBJ Audio EQ Cookbook 标准低通/高通
  系数，与 Salt `EqualizerFilterType` 全集（HighPass/LowPass/Peaking/
  LowShelf/HighShelf）对齐，Salt 预设导入无损。
- `EqCurveChart` 增加 LPF/HPF 精确二阶幅度响应曲线。

---

## 三、EQ 预设导入/导出（兼容 Salt Player）

`player/audiofx/EqPresetIO.kt`，JSON 格式与 Salt `EqualizerPresetBundle`
完全兼容（格式标识从 Salt APK 反编译实证）：

```json
{
  "format": "speq.equalizer-presets",
  "version": 1,
  "presets": [
    {
      "id": "style_bass",
      "name": "低音 Bass",
      "inputGain": 0.0,
      "outputGain": -2.0,
      "bands": [
        {"id": "b0", "type": "LowShelf", "frequency": 80.0, "gain": 6.0, "q": 0.7, "enabled": true}
      ]
    }
  ]
}
```

- 字段映射：`outputGain`（EQ 后防削波增益）↔ `preampDb`；`inputGain`
  导出固定 0；`enabled=false` 的频段导入时丢弃；type 枚举名一一对应。
- 导出：AudioFxScreen 顶栏导出按钮 → SAF `ACTION_CREATE_DOCUMENT`，
  导出「当前预设 + 全部自定义预设」合并 bundle。
- 导入：顶栏导入按钮 → SAF `ACTION_OPEN_DOCUMENT`，校验 format/version，
  落入"我的预设"分区（SharedPreferences 持久化，可单条删除）。
- 容错：兼容裸预设数组（无 bundle 包裹）的第三方导出格式。

---

## 四、编译验证过程中发现并修复的问题

全项目 kotlinc 真编译暴露出以下**真实缺陷**（此前仅靠人工阅读未发现）：

1. `MusicPlayer.kt` 缺 `import kotlinx.coroutines.delay`（`loadMetadata`
   轮询循环中的 `delay()`）。
2. `MusicPlayer.kt` 缺 `import com.coolplayer.music.data.AudioMetadata`
   （`loadMetadata` 里的 `val meta: AudioMetadata?`）。
3. `MusicPlayer.kt` 缺 `import com.coolplayer.music.data.toSongEntry`
   （`restoreLastSession` 里的 `entities[p]?.toSongEntry()`，该扩展函数
   本来就定义在 `data/Database.kt`，只是跨包需要 import）。
4. `PlayerScreen.kt` 第一个 `LyricsPane` 调用点漏传 `positionFlow`
   （position 局部化改造时的遗漏）。

以上 4 处均已修复并复验通过。

---

## 五、验证方法与结论

- **工具链**：Kotlin 2.0.20 编译器（Maven Central `kotlin-compiler-embeddable`）
  + Compose 编译器插件 `kotlin-compose-compiler-plugin-embeddable:2.0.20`
  + API 34 android.jar（Robolectric android-all）+ 项目真实依赖栈
  （media3 1.4.1 / Compose 1.7.2 / material3 1.3.0 / coil 2.7.0 /
  room 2.6.1 / navigation 2.8.1 / jaudiotagger 2.3.14）。
- **范围**：`app/src/main/java` 全部 100 个 Kotlin 源文件，0 error，
  464 个 class 文件产出。仅存原代码同款的 `Icons.Default.ArrowBack` /
  `Divider` 弃用告警（与改动前一致，非回归）。
- **AutoEQ catalog**：8850 条逐条断言（字段数、类型映射、频率范围、
  preamp 范围）全部通过；搜索逻辑端到端模拟验证通过。
- **结论**：语法、类型、API 签名层面全部验证。R8/资源合并/AGP 构建流程
  仍需在真开发环境跑一次 `./gradlew assembleDebug` 做最终确认。

---

## 六、后续路线图（未实施，供参考）

1. **gapless 无缝播放**：ExoPlayer 侧可考虑 `setNextMediaItem` 预加载 +
   自定义 AudioSink 连续输出；更彻底的做法是引入 Salt 同款 BASS
   （`BASS_STREAM_DECODE|PRESCAN` + `BASS_Mixer_StreamAddChannel`
   NORAMPIN），属播放核心替换，需单独任务。
2. **USB DAC 直写**（Salt `UsbAudioNative` JNI）：高阶需求，依赖 USB
   host 权限与设备探测，建议后续单独评估。
3. **DSD 解码**（Salt `BASS_CONFIG_DSD_GAIN`）：DoP/原生 DSD，需
   ffmpeg 或 BASS 扩展。
