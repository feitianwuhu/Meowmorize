package com.meowmorize.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 取某个时刻在指定时区里的「那一天」。
 *
 * 刻意不用 `LocalDate.ofInstant(instant, zone)`：那是 **Java 9** 追加的方法，
 * Android 上要 **API 34** 才有实现。本项目 minSdk 是 29，compileSdk 是 37，
 * 所以编译期一点问题都没有（android.jar 里有），装到 Android 13 及以下的机器上
 * 才会在运行期抛 `NoSuchMethodError`。
 *
 * 这个坑的表现极具误导性：崩溃发生在 Compose 首帧的组合过程里，抛的又是 Error
 * 不是 Exception，系统既不弹「应用停止运行」，应用自己也来不及画任何东西，
 * 用户看到的就是纯粹的「点开闪退」。定位它靠的是启动记录 + 崩溃堆栈，
 * 而不是任何静态检查——`NewApi` 这类 lint 也抓不到（它只比对 android.jar）。
 *
 * `Instant.atZone(zone).toLocalDate()` 是 API 26 起就有的组合，行为完全一致。
 */
fun Instant.toLocalDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
