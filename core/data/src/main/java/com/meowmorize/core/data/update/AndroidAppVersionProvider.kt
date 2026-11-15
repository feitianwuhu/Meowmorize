package com.meowmorize.core.data.update

import android.content.Context
import android.content.pm.PackageManager
import com.meowmorize.core.domain.app.AppVersionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 从 PackageManager 读当前安装版本。 */
@Singleton
class AndroidAppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppVersionProvider {

    override fun versionName(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"
}
