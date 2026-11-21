package com.meowmorize.feature.editor

import android.app.DatePickerDialog
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.meowmorize.core.designsystem.component.MarkdownText
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.meowTween
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowChip
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import com.meowmorize.core.designsystem.component.MeowPrimaryButton
import com.meowmorize.core.designsystem.component.MeowSegmented
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.designsystem.component.MeowTextField
import com.meowmorize.core.designsystem.component.SectionHeader
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.designsystem.theme.displayName
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ContentFormat
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.Subject

@Composable
fun EditorRoute(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) viewModel.recognizeImage(uri.toString())
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.recognizeImage(it.toString()) }
    }

    LaunchedEffect(state.saved, state.archived, state.deleted) {
        if (state.saved || state.archived || state.deleted) onDone()
    }

    EditorScreen(
        state = state,
        onBack = onDone,
        onTopicChange = viewModel::setTopic,
        onContentChange = viewModel::setContent,
        onSourceChange = viewModel::setSource,
        onSelectSubject = viewModel::setSubject,
        onDurationChange = viewModel::setDuration,
        onAutoDuration = viewModel::useAutoDuration,
        onPriorityChange = viewModel::setPriority,
        onResetScheduleChange = viewModel::setResetSchedule,
        onSave = viewModel::save,
        onArchive = viewModel::archive,
        onNewSubject = viewModel::openNewSubjectDialog,
        onHintChange = viewModel::setHint,
        onDeadlineChange = viewModel::setDeadline,
        onClozeAnswerChange = viewModel::setClozeAnswer,
        onDelete = viewModel::delete,
        onContentFormatChange = viewModel::setContentFormat,
        onDurationInputChange = viewModel::setDurationInput,
        onTakePhoto = {
            val file = File(context.cacheDir, "ocr_capture.jpg")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            cameraUri = uri
            takePicture.launch(uri)
        },
        onPickImage = { pickImage.launch("image/*") },
        onPasteText = {
            val pasted = clipboard.getText()?.text
            if (!pasted.isNullOrBlank()) {
                viewModel.setContent(
                    if (state.content.isBlank()) pasted else state.content + "\n\n" + pasted,
                )
            }
        },
        onDismissOcrMessage = viewModel::dismissOcrMessage,
    )

    if (state.showNewSubjectDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewSubjectDialog,
            title = { Text("新建学科") },
            text = {
                MeowTextField(
                    value = state.newSubjectName,
                    onValueChange = viewModel::setNewSubjectName,
                    label = "学科名称",
                    placeholder = "例如：英语、政治、专业课",
                    singleLine = false,
                    minLines = 1,
                    maxLines = 2,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmNewSubject) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNewSubjectDialog) { Text("取消") }
            },
            shape = RoundedCornerShape(26.dp),
        )
    }

}

@Composable
fun EditorScreen(
    state: EditorUiState,
    onBack: () -> Unit,
    onTopicChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onSelectSubject: (Long) -> Unit,
    onDurationChange: (Int) -> Unit,
    onAutoDuration: () -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onResetScheduleChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onNewSubject: () -> Unit,
    onHintChange: (String) -> Unit,
    onDeadlineChange: (java.time.Instant?) -> Unit,
    onClozeAnswerChange: (String) -> Unit,
    onDelete: () -> Unit,
    onContentFormatChange: (ContentFormat) -> Unit,
    onDurationInputChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onPasteText: () -> Unit,
    onDismissOcrMessage: () -> Unit,
) {
    var markdownPreview by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 110.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeowIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isEditing) "编辑任务" else "新增背诵任务",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (state.isEditing) "修改后排程是否更新，由你决定" else "内容越具体，复习时越容易想起来",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isEditing) {
                    MeowIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除任务",
                        onClick = { showDeleteDialog = true },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader(title = "学科")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.subjects, key = { it.id }) { subject ->
                    val selected = state.subjectId == subject.id
                    val color = Color(subject.colorArgb)
                    val chipShape = if (LocalAppStyle.current == AppStyle.TERMINAL) {
                        RoundedCornerShape(4.dp)
                    } else {
                        CircleShape
                    }
                    Row(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (selected) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                                chipShape,
                            )
                            .clickable { onSelectSubject(subject.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClick = onNewSubject)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "新建",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // 允许换行：从别处粘贴的标题常常自带换行，单行框会把它挤成一团
            MeowTextField(
                value = state.topic,
                onValueChange = onTopicChange,
                label = "背诵主题",
                placeholder = "例如：Unit 3 核心词汇",
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                isError = state.error != null && state.topic.isBlank(),
                supportingText = if (state.topic.isBlank()) "必填，用一句话说清这条要背什么" else null,
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "背诵内容",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (state.contentFormat == ContentFormat.MARKDOWN) {
                            "Markdown：标题 / 列表 / 两栏对照，复习时渲染后可读性更好"
                        } else {
                            "复习时先展示这一段，答案另外隐藏"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MeowChip(
                    text = if (state.contentFormat == ContentFormat.MARKDOWN) "高级已开" else "高级",
                    selected = state.contentFormat == ContentFormat.MARKDOWN,
                    onClick = {
                        onContentFormatChange(
                            if (state.contentFormat == ContentFormat.MARKDOWN) {
                                ContentFormat.PLAIN
                            } else {
                                ContentFormat.MARKDOWN
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            if (state.contentFormat == ContentFormat.MARKDOWN) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeowChip(
                        text = "编辑",
                        selected = !markdownPreview,
                        onClick = { markdownPreview = false },
                    )
                    MeowChip(
                        text = "预览",
                        selected = markdownPreview,
                        onClick = { markdownPreview = true },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "分栏可选「两栏」模板",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (markdownPreview) {
                    MeowCard {
                        if (state.content.isBlank()) {
                            Text(
                                text = "还没有内容可预览",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            MarkdownText(markdown = state.content)
                        }
                    }
                } else {
                    MarkdownContentEditor(
                        value = state.content,
                        onValueChange = onContentChange,
                    )
                }
            } else {
                MeowTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    label = "背诵内容",
                    placeholder = "把要背的内容贴进来。复习时会先遮住，让你先回忆。",
                    singleLine = false,
                    minLines = 6,
                    maxLines = 14,
                )
            }

            // 答案不再是「填空专属」：每条任务都有这一栏，留空则复习时
            // 按「先回忆、再显示内容」处理
            Spacer(Modifier.height(14.dp))
            MeowTextField(
                value = state.clozeAnswer,
                onValueChange = onClozeAnswerChange,
                label = "答案",
                placeholder = "例：突发性、破坏性、广泛性、不确定性",
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                supportingText = "复习时先展示「背诵内容」，答案默认隐藏，点「显示答案」才出现。" +
                    "留空则按「先回忆、再显示内容」处理",
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MeowOutlinedButton(
                    text = "拍照取字",
                    onClick = onTakePhoto,
                    enabled = !state.ocrBusy,
                    icon = Icons.Rounded.PhotoCamera,
                )
                MeowOutlinedButton(
                    text = "相册取字",
                    onClick = onPickImage,
                    enabled = !state.ocrBusy,
                    icon = Icons.Rounded.PhotoLibrary,
                )
                MeowOutlinedButton(
                    text = "粘贴文本",
                    onClick = onPasteText,
                    icon = Icons.Rounded.ContentPaste,
                )
                if (state.ocrBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (state.ocrAvailability) {
                    com.meowmorize.core.domain.repository.OcrAvailability.READY ->
                        "OCR（测试）：端侧识别，结果会追加到内容末尾。识别后务必核对错别字。"
                    com.meowmorize.core.domain.repository.OcrAvailability.PLAY_SERVICES_MISSING ->
                        "本机没有 Google Play 服务，端侧 OCR 不可用。可用「相册取字」外的系统工具提取文字后粘贴。"
                    else ->
                        "本机不支持端侧 OCR。可复制文本后直接粘贴，或手动输入。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.ocrMessage != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissOcrMessage),
                ) {
                    Text(
                        text = state.ocrMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            MeowTextField(
                value = state.hint,
                onValueChange = onHintChange,
                label = "背诵提示（可选）",
                placeholder = "例：先想「三次握手 → 防历史连接」，再展开",
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                supportingText = "复习页点「显示提示」才出现；用过提示后不能选「轻松」",
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "截止日期（可选）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (state.deadlineAt != null) {
                            "截止日越近，复习越密；不会让下一次复习排到截止之后"
                        } else {
                            "设定后临近日期会自动提高复习频率，并保证考前再见一次"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.deadlineAt != null) {
                    MeowTextButton(text = "清除", onClick = { onDeadlineChange(null) })
                }
                MeowOutlinedButton(
                    text = state.deadlineAt?.let {
                        it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M 月 d 日"))
                    } ?: "选择日期",
                    onClick = {
                        val current = state.deadlineAt ?: java.time.Instant.now()
                            .plus(java.time.Duration.ofDays(30))
                        val local = current.atZone(ZoneId.systemDefault())
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                // 截止时间统一定在当天 23:59，避免「考试当天早上」被算成已过期
                                onDeadlineChange(
                                    LocalDate.of(year, month + 1, day)
                                        .atTime(23, 59)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant(),
                                )
                            },
                            local.year,
                            local.monthValue - 1,
                            local.dayOfMonth,
                        ).show()
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            MeowTextField(
                value = state.source,
                onValueChange = onSourceChange,
                label = "来源（可选）",
                placeholder = "例如：考研词汇绿宝书 P32",
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                imeAction = androidx.compose.ui.text.input.ImeAction.Default,
            )

            Spacer(Modifier.height(18.dp))
            SectionHeader(
                title = "背诵用时",
                subtitle = "自动估算按字数推算，你更了解自己的速度",
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepSquare(text = "−", onClick = { onDurationChange(state.estimatedMinutes - 1) })
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = state.durationInput,
                    onValueChange = onDurationInputChange,
                    modifier = Modifier.width(96.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                    ),
                    suffix = { Text("分") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.size(8.dp))
                StepSquare(text = "+", onClick = { onDurationChange(state.estimatedMinutes + 1) })
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.durationIsCustom) "手动设定" else "自动估算",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (state.durationIsCustom) {
                            "可输入 3–60 之间的数字"
                        } else {
                            "改一下或直接输入即可自定义"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.durationIsCustom) {
                    MeowTextButton(text = "恢复自动", onClick = onAutoDuration)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "优先级", subtitle = "时间不够时，算法优先保留高优先级的内容")
            Spacer(Modifier.height(6.dp))
            MeowSegmented(
                options = listOf(Priority.LOW, Priority.NORMAL, Priority.HIGH).map { it.displayName() },
                selectedIndex = Priority.entries.indexOf(state.priority),
                onSelect = { index -> onPriorityChange(Priority.entries[index]) },
            )

            if (state.isEditing) {
                Spacer(Modifier.height(22.dp))
                SectionHeader(
                    title = "排程处理",
                    subtitle = "内容改动较大时可以选择重新学习，否则保留原有复习进度",
                )
                Spacer(Modifier.height(6.dp))
                PolicyOption(
                    title = "保持现有排程",
                    description = "只更新主题、内容与来源，下次复习时间不变。适合改错别字、补充例子。",
                    selected = !state.resetSchedule,
                    onClick = { onResetScheduleChange(false) },
                )
                Spacer(Modifier.height(8.dp))
                PolicyOption(
                    title = "按新内容重新学习",
                    description = "清空记忆进度，把这条当作新任务从今天重新排程。适合整段替换内容。",
                    selected = state.resetSchedule,
                    onClick = { onResetScheduleChange(true) },
                )

                Spacer(Modifier.height(18.dp))
                MeowTextButton(
                    text = "归档这条任务",
                    onClick = onArchive,
                    color = LocalMeowColors.current.danger,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (LocalAppStyle.current == AppStyle.GLASS) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            MeowPrimaryButton(
                text = if (state.isSaving) "保存中…" else "保存",
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这条任务？") },
            text = {
                Text(
                    "内容、背诵记录与复习日志会一起删除，无法恢复。" +
                        "如果只是暂时不想背，可以在任务库里归档，随时能恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(26.dp),
        )
    }
}

@Composable
private fun StepSquare(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PolicyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
