package com.meowmorize.core.domain.repository

/** 备份文件里的条数统计，用于界面提示。 */
data class BackupCounts(
    val subjects: Int = 0,
    val items: Int = 0,
)

/** 导入前先看一眼文件，好在弹窗里告诉用户「这是哪份备份、里面有没有记忆进度」。 */
data class BackupPreview(
    val counts: BackupCounts = BackupCounts(),
    /** 文件里是否真的带有记忆进度（决定导入弹窗要不要给出「恢复进度」的选项）。 */
    val hasProgress: Boolean = false,
)

/** 导入结果。 */
data class ImportSummary(
    val subjects: Int = 0,
    val items: Int = 0,
    /** 已存在（同「学科 + 主题」）而跳过的条数。 */
    val skipped: Int = 0,
    /** 实际恢复了记忆进度的条数（用户选择不导入、或文件本身没有进度时为 0）。 */
    val progressRestored: Int = 0,
)

/**
 * .mmr 备份（Meowmorize Memory Record）。
 *
 * 格式是带版本号的 JSON：`format` + `version` + 数据本体。
 * 兼容策略：**读取端只要求 `format` 匹配、`version` 不高于自身**，
 * 缺失字段一律取默认值——这样 v1 的文件在未来任何版本都能读，
 * 而新版本写出的文件在老版本上会明确拒绝（而不是读出一半）。
 *
 * 记忆进度是**双向可选**的：
 * - 导出方可选择不写入 `state`，得到的文件只含任务本身；
 * - 导入方可选择忽略文件里的 `state`，此时导入结果与「新建任务」完全等价
 *   （没有 `review_states` 记录 → 排程层按 NEW 处理，从今天开始安排）。
 *
 * 两边任意一边选择「不要进度」，结果都一样：任务内容完整、记忆进度归零。
 */
interface BackupRepository {

    suspend fun counts(): BackupCounts

    /** 读文件头，判断条数与是否含记忆进度。不写入任何数据。 */
    suspend fun peek(uriString: String): BackupPreview

    /**
     * 导出到 SAF 给定的目标；返回导出的条数。
     *
     * @param includeProgress 是否写入每条的记忆进度。false 时文件更小，
     *   任何导入方都只能拿到「新任务」。
     */
    suspend fun exportTo(uriString: String, includeProgress: Boolean = true): BackupCounts

    /**
     * 从 SAF 选中的文件导入（合并策略：同「学科 + 主题」跳过）。
     *
     * @param withProgress 是否恢复文件里的记忆进度。false 时即使文件带进度也一律忽略，
     *   导入后的任务与新建任务无区别。
     */
    suspend fun importFrom(uriString: String, withProgress: Boolean = true): ImportSummary
}
