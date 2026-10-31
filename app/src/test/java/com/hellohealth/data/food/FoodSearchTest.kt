package com.hellohealth.data.food

import com.hellohealth.data.local.entities.CachedFoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure ranking/tokenization rules for food search — no Room, no network. */
class FoodSearchTest {

    private fun food(id: String, name: String) = CachedFoodEntity(
        id = id, name = name, source = if (id.startsWith("seed:")) "seed" else "usda",
        basisUnit = "per_100g", caloriesPer = 100.0, lastRefreshedEpochMs = 1L,
    )

    @Test
    fun `tokenize lowercases, splits on whitespace, dedupes, and caps`() {
        assertEquals(listOf("chicken", "sandwich"), FoodSearch.tokenize("  Chicken   Sandwich "))
        assertEquals(listOf("egg"), FoodSearch.tokenize("egg egg egg"))
        assertTrue(FoodSearch.tokenize("a b c d e f g h").size <= FoodSearch.MAX_TOKENS)
    }

    @Test
    fun `likePattern wraps and escapes wildcards`() {
        assertEquals("%rice%", FoodSearch.likePattern("rice"))
        assertEquals("%50\\% off%", FoodSearch.likePattern("50% off"))
    }

    @Test
    fun `rank requires every token present, in any order`() {
        val candidates = listOf(
            food("seed:cb", "Chicken Breast"),
            food("seed:gb", "Grilled Beef"),
        )
        val hits = FoodSearch.rank(candidates, "breast chicken", 25).map { it.name }
        assertEquals(listOf("Chicken Breast"), hits)
    }

    @Test
    fun `rank orders exact, then prefix, then looser matches`() {
        val candidates = listOf(
            food("seed:sandwich", "Chicken Salad Sandwich"),
            food("seed:exact", "Chicken"),
            food("seed:prefix", "Chicken Breast"),
        )
        val hits = FoodSearch.rank(candidates, "chicken", 25).map { it.name }
        assertEquals(listOf("Chicken", "Chicken Breast", "Chicken Salad Sandwich"), hits)
    }

    @Test
    fun `rank prefers seed over remote on an otherwise exact tie`() {
        val candidates = listOf(
            food("usda:1", "Yogurt"),
            food("seed:y", "Yogurt"),
        )
        assertEquals("seed:y", FoodSearch.rank(candidates, "yogurt", 25).first().id)
    }

    @Test
    fun `rank dedupes by id and caps at the limit`() {
        val dupe = food("seed:x", "Apple")
        val hits = FoodSearch.rank(listOf(dupe, dupe, food("seed:y", "Apricot")), "ap", 1)
        assertEquals(1, hits.size)
    }

    @Test
    fun `blank query ranks to empty`() {
        assertTrue(FoodSearch.rank(listOf(food("seed:a", "Apple")), "   ", 25).isEmpty())
    }
}
