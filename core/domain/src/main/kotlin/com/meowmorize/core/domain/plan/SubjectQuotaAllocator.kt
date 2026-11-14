package com.meowmorize.core.domain.plan

/**
 * 学科配额分配。
 *
 * 只解决一个具体问题：**过载时不要被某一科吃满**。
 * 以前按优先级全局排序，只要「专业课」到期任务多，英语就会连续几天排不进去；
 * 用户感受是「算法忘了我还有别的科目」。
 *
 * 做法（全部按分钟）：
 * 1. 算出每科需求；
 * 2. 按学科权重分容量，并夹在 [minShare, maxShare] 区间；
 * 3. 夹取后总和与容量不等，再做一次「多的退、少的补」；
 * 4. 每科至少保留 1 条的量（若容量允许），避免份额算出来是 0。
 *
 * 不在过载时（需求 ≤ 容量）完全不介入——配额是为了处理稀缺，不是为了平均主义。
 */
object SubjectQuotaAllocator {

    data class Candidate(
        val itemId: Long,
        val subjectId: Long?,
        val minutes: Int,
    )

    data class Result(
        /** 每个学科可分到的分钟数；未列出的学科为 0。 */
        val budgets: Map<Long?, Int>,
        /** 是否真正生效（需求超过容量且学科数 ≥ 2）。 */
        val applied: Boolean,
        val notes: List<String> = emptyList(),
    )

    fun allocate(
        candidates: List<Candidate>,
        capacityMinutes: Int,
        weights: Map<Long, Double> = emptyMap(),
        minShare: Double = 0.2,
        maxShare: Double = 0.6,
    ): Result {
        if (candidates.isEmpty() || capacityMinutes <= 0) {
            return Result(emptyMap(), applied = false)
        }
        val demand = candidates.groupBy { it.subjectId }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }
        val total = demand.values.sum()
        if (total <= capacityMinutes || demand.size < 2) {
            return Result(demand, applied = false)
        }

        val count = demand.size
        // 学科多到「最小份额之和超过 1」时，按学科数平分，否则约束无解
        val effectiveMin = minOf(minShare, 1.0 / count)
        val effectiveMax = maxOf(maxShare, effectiveMin)

        val weightOf = { id: Long? -> (id?.let { weights[it] } ?: 1.0).coerceIn(0.3, 3.0) }
        val totalWeight = demand.keys.sumOf { weightOf(it) }

        val budgets = demand.mapValues { (id, need) ->
            val raw = capacityMinutes * weightOf(id) / totalWeight
            raw.coerceIn(capacityMinutes * effectiveMin, capacityMinutes * effectiveMax)
                .coerceAtMost(need.toDouble())
                .toInt()
        }.toMutableMap()

        // 多退少补：先退超出部分的富余，再补给还有需求但没吃饱的学科
        var guard = 0
        while (guard++ < 8) {
            val sum = budgets.values.sum()
            if (sum == capacityMinutes) break
            if (sum > capacityMinutes) {
                val overflow = sum - capacityMinutes
                val reducible = budgets.filterValues { it > 0 }.keys.toList()
                if (reducible.isEmpty()) break
                val totalReducible = reducible.sumOf { budgets[it] ?: 0 }
                if (totalReducible <= 0) break
                reducible.forEach { id ->
                    val share = (budgets[id] ?: 0).toDouble() / totalReducible
                    val cut = (overflow * share).toInt()
                    budgets[id] = ((budgets[id] ?: 0) - cut).coerceAtLeast(0)
                }
            } else {
                val gap = capacityMinutes - sum
                val hungry = budgets.filter { (id, value) -> value < (demand[id] ?: 0) }
                if (hungry.isEmpty()) break
                val weightsSum = hungry.keys.sumOf { weightOf(it) }
                var added = 0
                hungry.forEach { (id, value) ->
                    val add = (gap * weightOf(id) / weightsSum).toInt()
                        .coerceAtMost((demand[id] ?: 0) - value)
                    budgets[id] = value + add
                    added += add
                }
                if (added == 0) break
            }
        }

        // 每科保底 1 条：只在不超出容量的前提下补进去，绝不为了平均而超发
        val shortestBySubject = candidates.groupBy { it.subjectId }
            .mapValues { (_, list) -> list.minOf { it.minutes } }
        shortestBySubject.forEach { (id, shortest) ->
            val current = budgets[id] ?: 0
            val used = budgets.values.sum()
            if (current <= 0 && used + shortest <= capacityMinutes) {
                budgets[id] = shortest
            }
        }

        val notes = mutableListOf<String>()
        val over = budgets.filterKeys { (demand[it] ?: 0) > (budgets[it] ?: 0) }
        if (over.isNotEmpty()) {
            notes += "按学科配额分配，未排满的科目今天只保留一部分，其余顺延。"
        }
        return Result(budgets, applied = true, notes = notes)
    }
}
