package com.meowmorize.core.data.seed

import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.usecase.PrepareTodayPlanUseCase
import com.meowmorize.core.model.ItemDraft
import com.meowmorize.core.model.Priority
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动的示例数据。
 *
 * 空应用会让人不知道从哪开始，因此预置三个学科与三条示例任务，
 * 内容明确标注「示例」，用户可以直接编辑或删除。
 */
@Singleton
class DemoDataInitializer @Inject constructor(
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
    private val prepareTodayPlan: PrepareTodayPlanUseCase,
) {
    suspend fun initialize() {
        val settings = settingsRepository.current()
        if (settings.onboarded) return

        val english = studyRepository.ensureSubject("英语")
        val politics = studyRepository.ensureSubject("政治")
        val major = studyRepository.ensureSubject("专业课")

        studyRepository.createItem(
            ItemDraft(
                subjectId = english.id,
                topic = "Unit 1 高频词汇（示例）",
                content = "abandon /əˈbændən/ v. 放弃；抛弃\n" +
                    "例句：She abandoned the plan after careful consideration.\n" +
                    "搭配：abandon oneself to 沉溺于",
                source = "示例数据 · 可编辑或删除",
                priority = Priority.HIGH,
            ),
        )
        studyRepository.createItem(
            ItemDraft(
                subjectId = politics.id,
                topic = "矛盾的普遍性与特殊性（示例）",
                content = "矛盾的普遍性：矛盾存在于一切事物中，并贯穿事物发展过程的始终。\n" +
                    "矛盾的特殊性：具体事物的矛盾各有其特点。\n" +
                    "方法论意义：具体问题具体分析是马克思主义活的灵魂。",
                source = "示例数据 · 可编辑或删除",
                priority = Priority.NORMAL,
            ),
        )
        studyRepository.createItem(
            ItemDraft(
                subjectId = major.id,
                topic = "TCP 三次握手（示例）",
                content = "1. 客户端发送 SYN（seq=x），进入 SYN_SENT；\n" +
                    "2. 服务端回复 SYN+ACK（seq=y, ack=x+1），进入 SYN_RCVD；\n" +
                    "3. 客户端发送 ACK（ack=y+1），双方进入 ESTABLISHED。\n" +
                    "三次而非两次：避免历史重复连接请求造成资源浪费。",
                source = "示例数据 · 可编辑或删除",
                priority = Priority.NORMAL,
            ),
        )

        settingsRepository.update { it.copy(onboarded = true) }
        prepareTodayPlan(force = true)
    }
}
