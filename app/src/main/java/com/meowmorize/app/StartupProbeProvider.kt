package com.meowmorize.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * 进程启动探针。
 *
 * 启动顺序是 attachBaseContext → Application.onCreate → 本 Provider.onCreate → Activity，
 * 所以这一笔用来卡住「Application 起来了，但走到 Activity 之前」这一段。
 * 配合启动记录可以区分四种情况：
 *
 * - 连 attachBaseContext 都没有 → 崩在系统加载阶段（dex / 资源 / manifest / so / 校验）
 * - 有 attachBaseContext、没有 Application.onCreate → 崩在 Application 构造或 Hilt 依赖图
 * - 有 Application.onCreate、没有 provider.onCreate → 崩在 Application.onCreate 内部
 * - 三者都有 → 崩在 Activity / 首帧
 *
 * 不做任何业务，也不读数据库，只为留下一个时间戳。
 */
class StartupProbeProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { ctx ->
            // 顺序很重要：先装崩溃处理器，后面任何一步崩了都有堆栈
            CrashLog.install(ctx)
            StartupTrace.mark(ctx, "provider.onCreate")
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
