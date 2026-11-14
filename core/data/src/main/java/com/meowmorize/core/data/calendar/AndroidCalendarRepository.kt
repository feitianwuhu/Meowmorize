package com.meowmorize.core.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.database.dao.CalendarSyncDao
import com.meowmorize.core.database.entity.CalendarSyncEntity
import com.meowmorize.core.domain.repository.CalendarSyncEvent
import com.meowmorize.core.domain.repository.CalendarSyncRepository
import com.meowmorize.core.domain.repository.CalendarSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统日历适配器（CalendarContract 实现）。
 *
 * 关键点：
 * - 优先复用本应用创建的日历账户（MeowMorize），找不到就用主日历，再不行才尝试创建本地日历；
 *   不去碰用户已有的日历账户，避免污染。
 * - 事件与任务通过 `calendar_sync` 表关联，重复同步只更新不新增。
 * - 所有异常（无权限、厂商 ROM 限制 provider）都收敛为 [CalendarSyncResult]，由 UI 给提示，
 *   绝不让「同步日历」把应用搞崩。
 */
@Singleton
class AndroidCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarSyncDao: CalendarSyncDao,
    private val timeProvider: TimeProvider,
) : CalendarSyncRepository {

    override fun hasPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun sync(events: List<CalendarSyncEvent>): CalendarSyncResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext CalendarSyncResult(permissionDenied = true)

            runCatching {
                val calendarId = findOrCreateCalendarId()
                    ?: return@withContext CalendarSyncResult(failed = true)

                val existing = calendarSyncDao.getAll().associateBy { it.itemId }
                val keep = events.map { it.itemId }.toSet()
                var synced = 0
                var removed = 0

                events.forEach { event ->
                    val previous = existing[event.itemId]
                    val eventId = if (previous != null && eventExists(previous.eventId)) {
                        updateEvent(previous.eventId, event, calendarId)
                        previous.eventId
                    } else {
                        insertEvent(event, calendarId)
                    }

                    if (eventId != null) {
                        calendarSyncDao.upsert(
                            CalendarSyncEntity(
                                itemId = event.itemId,
                                eventId = eventId,
                                syncedAt = timeProvider.now().toEpochMilli(),
                            ),
                        )
                        synced++
                    }
                }

                // 已不在窗口内的任务：删除对应日程与映射
                existing.values.filter { it.itemId !in keep }.forEach { mapping ->
                    deleteEvent(mapping.eventId)
                    calendarSyncDao.deleteByItem(mapping.itemId)
                    removed++
                }

                CalendarSyncResult(synced = synced, removed = removed)
            }.getOrElse {
                CalendarSyncResult(failed = true)
            }
        }

    override suspend fun clear(): CalendarSyncResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CalendarSyncResult(permissionDenied = true)
        runCatching {
            val mappings = calendarSyncDao.getAll()
            mappings.forEach { deleteEvent(it.eventId) }
            calendarSyncDao.deleteAll()
            CalendarSyncResult(removed = mappings.size)
        }.getOrElse { CalendarSyncResult(failed = true) }
    }

    private fun findOrCreateCalendarId(): Long? {
        queryCalendar("${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL))?.let { return it }

        queryCalendar("${CalendarContract.Calendars.IS_PRIMARY}=1", null)?.let { return it }
        queryCalendar(null, null)?.let { return it }

        return createLocalCalendar()
    }

    private fun queryCalendar(selection: String?, selectionArgs: Array<String>?): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val access = cursor.getInt(1)
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return cursor.getLong(0)
            }
            null
        }
    }

    private fun createLocalCalendar(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Meowmorize")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFFFF6A2B.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        return runCatching {
            context.contentResolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }

    private fun insertEvent(event: CalendarSyncEvent, calendarId: Long): Long? {
        val values = event.toContentValues(calendarId)
        return runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }

    private fun updateEvent(eventId: Long, event: CalendarSyncEvent, calendarId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return runCatching {
            context.contentResolver.update(uri, event.toContentValues(calendarId), null, null) > 0
        }.getOrDefault(false)
    }

    private fun deleteEvent(eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun eventExists(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return runCatching {
            context.contentResolver.query(uri, arrayOf(CalendarContract.Events._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    private fun CalendarSyncEvent.toContentValues(calendarId: Long): ContentValues {
        val startMillis = startAt.toEpochMilli()
        val endMillis = startMillis + minutes.coerceAtLeast(1) * 60_000L
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeProvider.zone().id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
    }

    private companion object {
        const val ACCOUNT_NAME = "Meowmorize"
    }
}
