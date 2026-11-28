package com.meowmorize.core.domain.repository

/** 备份文件里的条数统计，用于界面提示。 */
data class BackupCounts(
    val subjects: Int = 0,
    val items: Int = 0,
)

/** 导入结果。 */
data class ImportSummary(
    val subjects: Int = 0,
    val items: Int = 0,
    /** 已存在（同「学科 + 主题」）而跳过的条数。 */
    val skipped: Int = 0,
)

/**
 * .mmr 备份（Meowmorize Memory Record）。
 *
 * 格式是带版本号的 JSON：`format` + `version` + 数据本体。
 * 兼容策略：**读取端只要求 `format` 匹配、`version` 不高于自身**，
 * 缺失字段一律取默认值——这样 v1 的文件在未来任何版本都能读，
 * 而新版本写出的文件在老版本上会明确拒绝（而不是读出一半）。
 */
interface BackupRepository {

    suspend fun counts(): BackupCounts

    /** 导出到 SAF 给定的目标；返回导出的条数。 */
    suspend fun exportTo(uriString: String): BackupCounts

    /** 从 SAF 选中的文件导入（合并策略：同「学科 + 主题」跳过）。 */
    suspend fun importFrom(uriString: String): ImportSummary
}
