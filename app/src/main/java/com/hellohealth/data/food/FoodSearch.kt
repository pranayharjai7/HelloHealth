package com.hellohealth.data.food

import com.hellohealth.data.local.entities.CachedFoodEntity

/**
 * Pure (Room-free, network-free) helpers for food search: tokenization, the local `LIKE`-pattern
 * builder, and relevance ranking. Extracted so the matching rules are unit-testable in isolation and
 * shared by [com.hellohealth.data.repository.NutritionRepositoryImpl.searchFoods].
 *
 * Matching is **token-AND, order-independent**: every whitespace word in the query must appear
 * somewhere in the food name, in any order ("breast chicken" finds "Chicken Breast"). Ranking then
 * promotes the most literal matches (exact → prefix → in-order → any-order), prefers shorter names,
 * and — on ties — the bundled `seed:` catalog over remote `usda:`/`off:` rows.
 */
object FoodSearch {

    /** Cap tokens so a pathological query can't build an unbounded SQL clause. */
    const val MAX_TOKENS = 6

    /** Split a query into lowercased, de-duplicated word tokens (whitespace-separated), capped. */
    fun tokenize(query: String): List<String> =
        query.trim().lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TOKENS)

    /** Escape a single token's literal LIKE wildcards and wrap it as a `%token%` contains-pattern. */
    fun likePattern(token: String): String {
        val escaped = token
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    /**
     * Rank + de-duplicate (by id) the merged local+remote candidates against the raw [query], best
     * first, then truncate to [limit]. Stable within a relevance bucket (sortedBy is stable), so the
     * DAO's own order and the merge order are preserved on exact ties.
     */
    fun rank(candidates: List<CachedFoodEntity>, query: String, limit: Int): List<CachedFoodEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val tokens = tokenize(query)
        return candidates
            .distinctBy { it.id }
            .filter { food -> tokens.all { food.name.lowercase().contains(it) } }
            .sortedWith(
                compareBy(
                    { scoreTier(it.name, q, tokens) },      // 0 = best
                    { it.name.length },                      // shorter names first
                    { sourceTier(it.id) },                   // seed over remote on ties
                    { it.name.lowercase() },                 // stable alphabetical
                )
            )
            .take(limit)
    }

    /** Lower is better. 0 exact, 1 prefix, 2 all-tokens-in-order, 3 all-tokens-any-order. */
    private fun scoreTier(name: String, q: String, tokens: List<String>): Int {
        val n = name.lowercase()
        return when {
            n == q -> 0
            n.startsWith(q) -> 1
            tokensInOrder(n, tokens) -> 2
            else -> 3
        }
    }

    /** True if every token appears in [name] left-to-right in the given order (gaps allowed). */
    private fun tokensInOrder(name: String, tokens: List<String>): Boolean {
        var from = 0
        for (t in tokens) {
            val idx = name.indexOf(t, from)
            if (idx < 0) return false
            from = idx + t.length
        }
        return true
    }

    /** Bundled catalog (`seed:`) ranks above remote rows; everything else is neutral. */
    private fun sourceTier(id: String): Int = if (id.startsWith("seed:")) 0 else 1
}
