package com.meowmorize.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meowmorize.core.model.UpdateInfo

/**
 * 启动时「发现新版本」的提示。
 *
 * 刻意做得克制：它只是一个提示——不下载、不跳转、不打断当前操作，
 * 点「稍后」就彻底消失（同一版本本次启动内不再弹）。
 * 是否更新、什么时候更新，完全由用户决定。
 */
@Composable
internal fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column {
                Text(
                    text = "当前版本可以继续用。要看更新了什么、要不要装，都由你决定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = info.notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onOpen(info.bestUrl)
                    onDismiss()
                },
            ) {
                Text("去下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        },
    )
}
