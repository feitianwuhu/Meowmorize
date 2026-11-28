package com.meowmorize.core.data.backup

import android.content.Context
import android.net.Uri
import com.meowmorize.core.database.dao.ItemDao
import com.meowmorize.core.database.dao.ReviewStateDao
import com.meowmorize.core.database.dao.SubjectDao
import com.meowmorize.core.database.entity.ItemEntity
import com.meowmorize.core.database.entity.ReviewStateEntity
import com.meowmorize.core.database.entity.SubjectEntity
import com.meowmorize.core.domain.repository.BackupCounts
import com.meowmorize.core.domain.repository.BackupPreview
import com.meowmorize.core.domain.repository.BackupRepository
import com.meowmorize.core.domain.repository.ImportSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * .mmr 备份实现。
 *
 * 格式（v1，字段全部可选，读取端按默认值补齐）：
 * ```json
 * {
 *   "format": "meowmorize.mmr",
 *   "version": 1,
 *   "exportedAt": "2026-11-28T08:00:00Z",
 *   "includeProgress": true,
 *   "subjects": [{ "name": "英语", "colorArgb": -16776961, "weight": 1.0, "sortOrder": 0 }],
 *   "items": [{
 *     "topic": "...", "content": "...", "answer": "...", "source": null,
 *     "contentFormat": "PLAIN", "priority": "NORMAL", "status": "ACTIVE",
 *     "estimatedMinutes": 10, "durationIsCustom": false,
 *     "createdAt": 1732752000000, "updatedAt": 1732752000000,
 *     "subjectName": "英语",
 *     "state": { "dueAt": 1733356800000, "stability": 3.2, "difficulty": 5.0,
 *                "reps": 2, "lapses": 0, "phase": "REVIEW", "scheduledDays": 3,
 *                "leech": false }
 *   }]
 * }
 * ```
 *
 * 几个刻意的取舍：
 * - **记忆进度是可选内容**：`includeProgress = false` 时一条 `state` 都不写。
 *   老版本应用读这种文件时找不到 `state`，自然按新任务处理——不需要额外约定。
 * - **不导出复习日志**：它是历史明细，体积大且不影响恢复后的排程；
 *   记忆状态（stability / difficulty / dueAt）已经足够继续学习。
 * - **导入用合并而非覆盖**：同「学科 + 主题」视为同一条，跳过并计数，
 *   避免把用户导入前的改动冲掉。确需替换时先删再导。
 * - **id 不参与导入**：主键在目标库里重新分配，学科按名称匹配。
 */
@Singleton
class MmrBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subjectDao: SubjectDao,
    private val itemDao: ItemDao,
    private val reviewStateDao: ReviewStateDao,
) : BackupRepository {

    override suspend fun counts(): BackupCounts = withContext(Dispatchers.IO) {
        BackupCounts(
            subjects = subjectDao.getAll().size,
            items = itemDao.observeAll().first().size,
        )
    }

    override suspend fun peek(uriString: String): BackupPreview = withContext(Dispatchers.IO) {
        val root = readRoot(uriString)
        val items = root.optJSONArray("items")
        val itemCount = items?.length() ?: 0
        BackupPreview(
            counts = BackupCounts(
                subjects = root.optJSONArray("subjects")?.length() ?: 0,
                items = itemCount,
            ),
            // 判据用「文件里实际有没有 state」，而不是只看 includeProgress 标记：
            // 任务全是新任务时标记为 true 但确实没东西可恢复，这时不该给用户一个空选项
            hasProgress = items.hasAnyState(),
        )
    }

    override suspend fun exportTo(
        uriString: String,
        includeProgress: Boolean,
    ): BackupCounts = withContext(Dispatchers.IO) {
        val subjects = subjectDao.getAll()
        val subjectNames = subjects.associate { it.id to it.name }
        val items = itemDao.observeAll().first()
        // 不含进度时干脆不查，避免白读一遍全表
        val states = if (includeProgress) {
            reviewStateDao.getAll().associateBy { it.itemId }
        } else {
            emptyMap()
        }

        val root = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", nowIso())
            // 显式记录导出意图：导入方与排查问题的人都能一眼看出这份文件有没有进度
            put("includeProgress", includeProgress)
            put("subjects", JSONArray().apply {
                subjects.forEach { subject ->
                    put(
                        JSONObject().apply {
                            put("name", subject.name)
                            put("colorArgb", subject.colorArgb)
                            put("sortOrder", subject.sortOrder)
                            put("weight", subject.weight)
                            subject.examAt?.let { put("examAt", it) }
                        },
                    )
                }
            })
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("topic", item.topic)
                            put("content", item.content)
                            item.clozeAnswer?.let { put("answer", it) }
                            item.source?.let { put("source", it) }
                            put("contentFormat", item.contentFormat)
                            put("priority", item.priority)
                            put("status", item.status)
                            put("estimatedMinutes", item.estimatedMinutes)
                            put("durationIsCustom", item.durationIsCustom)
                            put("createdAt", item.createdAt)
                            put("updatedAt", item.updatedAt)
                            item.hint?.let { put("hint", it) }
                            item.deadlineAt?.let { put("deadlineAt", it) }
                            subjectNames[item.subjectId]?.let { put("subjectName", it) }
                            if (includeProgress) {
                                states[item.id]?.let { state -> put("state", state.toJson()) }
                            }
                        },
                    )
                }
            })
        }

        val resolver = context.contentResolver
        resolver.openOutputStream(Uri.parse(uriString))?.use { stream ->
            stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法写入目标文件")

        BackupCounts(subjects = subjects.size, items = items.size)
    }

    override suspend fun importFrom(
        uriString: String,
        withProgress: Boolean,
    ): ImportSummary = withContext(Dispatchers.IO) {
        val root = readRoot(uriString)

        // 学科：按名称匹配，不存在则新建
        var createdSubjects = 0
        val nameToId = mutableMapOf<String, Long>()
        root.optJSONArray("subjects")?.let { array ->
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val name = json.optString("name").trim()
                if (name.isEmpty()) continue
                val existing = subjectDao.findByName(name)
                if (existing != null) {
                    nameToId[name] = existing.id
                } else {
                    val id = subjectDao.insert(
                        SubjectEntity(
                            name = name,
                            colorArgb = json.optInt("colorArgb", DEFAULT_SUBJECT_COLOR),
                            sortOrder = json.optInt("sortOrder", 0),
                            createdAt = System.currentTimeMillis(),
                            weight = json.optDouble("weight", 1.0),
                            examAt = json.optLong("examAt", 0L).takeIf { it > 0L },
                        ),
                    )
                    nameToId[name] = id
                    createdSubjects++
                }
            }
        }

        // 任务：同「学科 + 主题」视为已存在
        val existingKeys = itemDao.observeAll().first()
            .map { keyOf(it.subjectId, it.topic) }
            .toMutableSet()

        var imported = 0
        var skipped = 0
        var restored = 0
        root.optJSONArray("items")?.let { array ->
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val topic = json.optString("topic").trim()
                val content = json.optString("content")
                if (topic.isEmpty() || content.isBlank()) {
                    skipped++
                    continue
                }
                val subjectName = json.optString("subjectName").takeIf { it.isNotBlank() }
                val subjectId = subjectName?.let { nameToId[it] }
                val key = keyOf(subjectId, topic)
                if (key in existingKeys) {
                    skipped++
                    continue
                }

                val now = System.currentTimeMillis()
                val itemId = itemDao.insert(
                    ItemEntity(
                        subjectId = subjectId,
                        topic = topic,
                        content = content,
                        source = json.optString("source").takeIf { it.isNotBlank() },
                        priority = json.optString("priority", "NORMAL"),
                        status = json.optString("status", "ACTIVE"),
                        estimatedMinutes = json.optInt("estimatedMinutes", 5).coerceIn(1, 240),
                        createdAt = json.optLong("createdAt", now),
                        updatedAt = json.optLong("updatedAt", now),
                        durationIsCustom = json.optBoolean("durationIsCustom", false),
                        hint = json.optString("hint").takeIf { it.isNotBlank() },
                        contentFormat = json.optString("contentFormat", "PLAIN"),
                        deadlineAt = json.optLong("deadlineAt", 0L).takeIf { it > 0L },
                        clozeAnswer = json.optString("answer").takeIf { it.isNotBlank() },
                    ),
                )
                existingKeys += key
                imported++

                // 用户选择「不导入进度」时，即使文件里有 state 也一律不写：
                // 没有 review_states 记录 → 排程层按 NEW 处理，与新建任务完全一致
                if (withProgress) {
                    val state = json.optJSONObject("state")
                    if (state != null) {
                        reviewStateDao.upsert(state.toEntity(itemId))
                        restored++
                    }
                }
            }
        }

        ImportSummary(
            subjects = createdSubjects,
            items = imported,
            skipped = skipped,
            progressRestored = restored,
        )
    }

    // ---------- 文件读取与校验 ----------

    /** 读文件、校验格式与版本。peek 与 import 共用，保证两边判断一致。 */
    private fun readRoot(uriString: String): JSONObject {
        val text = context.contentResolver.openInputStream(Uri.parse(uriString))
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IOException("无法读取所选文件")

        val root = runCatching { JSONObject(text) }
            .getOrElse { throw IOException("文件不是有效的 .mmr 备份") }

        val format = root.optString("format")
        if (format != FORMAT && format != "meowmorize") {
            throw IOException("文件格式不匹配（期望 $FORMAT）")
        }
        val version = root.optInt("version", 1)
        if (version > VERSION) {
            throw IOException("备份来自更新版本的 Meowmorize（v$version），请先升级应用")
        }
        return root
    }

    private fun JSONArray?.hasAnyState(): Boolean {
        val array = this ?: return false
        for (index in 0 until array.length()) {
            if (array.optJSONObject(index)?.has("state") == true) return true
        }
        return false
    }

    // ---------- JSON 互转 ----------

    private fun ReviewStateEntity.toJson(): JSONObject = JSONObject().apply {
        put("dueAt", dueAt)
        put("stability", stability)
        put("difficulty", difficulty)
        put("reps", reps)
        put("lapses", lapses)
        put("phase", phase)
        put("scheduledDays", scheduledDays)
        put("leech", leech)
        lastReviewedAt?.let { put("lastReviewedAt", it) }
        leechSince?.let { put("leechSince", it) }
        put("learningStep", learningStep)
        put("sameDayReps", sameDayReps)
    }

    private fun JSONObject.toEntity(itemId: Long): ReviewStateEntity = ReviewStateEntity(
        itemId = itemId,
        // 缺失或异常值一律回落到「现在就该复习」，宁可早复习也不要导入出一个永不到期的幽灵状态
        dueAt = optLong("dueAt", System.currentTimeMillis()),
        stability = optDouble("stability", 1.0).coerceAtLeast(0.1),
        difficulty = optDouble("difficulty", 5.0).coerceIn(1.0, 10.0),
        reps = optInt("reps", 0).coerceAtLeast(0),
        lapses = optInt("lapses", 0).coerceAtLeast(0),
        phase = optString("phase", "NEW"),
        lastReviewedAt = optLong("lastReviewedAt", 0L).takeIf { it > 0L },
        scheduledDays = optInt("scheduledDays", 0).coerceAtLeast(0),
        leech = optBoolean("leech", false),
        leechSince = optLong("leechSince", 0L).takeIf { it > 0L },
        learningStep = optInt("learningStep", 0),
        sameDayReps = optInt("sameDayReps", 0),
    )

    private fun keyOf(subjectId: Long?, topic: String): String = "${subjectId ?: -1}|$topic"

    private fun nowIso(): String = java.text.SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        java.util.Locale.US,
    ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date())

    private companion object {
        const val FORMAT = "meowmorize.mmr"

        /**
         * 读取端支持的**最高**版本；写出的文件始终用这个版本。
         *
         * `includeProgress` 是纯追加字段，老版本读到会忽略，因此不需要升版本。
         */
        const val VERSION = 1
        const val DEFAULT_SUBJECT_COLOR = 0xFFF97316.toInt()
    }
}
