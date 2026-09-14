package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.exercise.ExerciseAssetLoader
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.ExerciseDao
import com.hellohealth.data.local.entities.ExerciseEntity
import com.hellohealth.data.local.entities.toJsonString
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository tests for the read-only exercise catalog. Uses a real in-memory Room DAO (house style —
 * no mockk) and a controllable [ExerciseAssetLoader] fake so seed/idempotency/parse-failure paths are
 * exercised against real SQL. The real bundled `exercises.json` is covered by the on-device seed QA;
 * here we pin the count-gating and no-crash guarantees deterministically.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExerciseDao

    /** Loader whose result is set per-test — success with a fixed list, or a failure. */
    private class FakeLoader(
        context: Context,
        var result: Result<List<ExerciseEntity>>,
    ) : ExerciseAssetLoader(context) {
        var loadCalls = 0
        override fun loadExercises(): Result<List<ExerciseEntity>> {
            loadCalls++
            return result
        }
    }

    private fun entity(id: String, name: String, category: String = "strength") = ExerciseEntity(
        id = id, name = name, category = category,
        primaryMuscles = listOf("chest").toJsonString(),
        secondaryMuscles = listOf("triceps").toJsonString(),
        equipment = "barbell",
        instructions = listOf("Step 1", "Step 2").toJsonString(),
        gifUrl = "https://example/$id.jpg",
        youtubeQuery = "$name tutorial",
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.exerciseDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(loader: ExerciseAssetLoader) = ExerciseRepositoryImpl(dao, loader)

    @Test
    fun `seedIfEmpty inserts on empty table and reports the count`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.success(listOf(entity("bench", "Bench Press"), entity("squat", "Squat", "strength")))
        )

        val count = repo(loader).seedIfEmpty()

        assertEquals(2, count)
        assertEquals(2, dao.count())
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `seedIfEmpty is idempotent - second call does not reload or duplicate`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.success(listOf(entity("bench", "Bench Press")))
        )
        val repo = repo(loader)

        repo.seedIfEmpty()
        val secondCount = repo.seedIfEmpty()

        assertEquals(1, secondCount)
        assertEquals(1, dao.count())
        // The gate short-circuits before the loader on the second call.
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `seedIfEmpty leaves table empty on parse failure and retries next call`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.failure(IllegalStateException("corrupt asset"))
        )
        val repo = repo(loader)

        val firstCount = repo.seedIfEmpty()

        assertEquals("failed load seeds nothing", 0, firstCount)
        assertEquals(0, dao.count())

        // Recovery: a later launch with a healthy asset seeds successfully (count still 0 → retry runs).
        loader.result = Result.success(listOf(entity("bench", "Bench Press")))
        val recoveredCount = repo.seedIfEmpty()

        assertEquals(1, recoveredCount)
        assertEquals(1, dao.count())
        assertEquals(2, loader.loadCalls) // retried because the table was still empty
    }

    @Test
    fun `seedIfEmpty treats an empty parsed list as unseeded and retries`() = runTest {
        val loader = FakeLoader(ApplicationProvider.getApplicationContext(), Result.success(emptyList()))
        val repo = repo(loader)

        assertEquals(0, repo.seedIfEmpty())
        assertEquals(0, dao.count())

        loader.result = Result.success(listOf(entity("bench", "Bench Press")))
        assertEquals(1, repo.seedIfEmpty())
    }

    @Test
    fun `search matches name and category, maps to domain, and honors limit`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.success(
                listOf(
                    entity("bench", "Bench Press", "strength"),
                    entity("incline-bench", "Incline Bench Press", "strength"),
                    entity("plank", "Plank", "core"),
                )
            )
        )
        repo(loader).seedIfEmpty()

        val benchHits = repo(loader).search("bench", limit = 50)
        assertEquals(2, benchHits.size)
        assertTrue(benchHits.all { it.name.contains("Bench") })
        // domain mapping decoded the JSON list columns
        assertEquals(listOf("chest"), benchHits.first().primaryMuscles)

        val byCategory = repo(loader).search("core", limit = 50)
        assertEquals(1, byCategory.size)
        assertEquals("Plank", byCategory.first().name)

        val limited = repo(loader).search("bench", limit = 1)
        assertEquals(1, limited.size)
    }

    @Test
    fun `search treats a literal percent as text, not a wildcard`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.success(listOf(entity("bench", "Bench Press")))
        )
        repo(loader).seedIfEmpty()

        // A raw "%" would match everything if unescaped; escaped, it matches nothing here.
        assertTrue(repo(loader).search("%", limit = 50).isEmpty())
    }

    @Test
    fun `getById and getByIds map to domain and handle misses`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.success(listOf(entity("bench", "Bench Press"), entity("squat", "Squat")))
        )
        val repo = repo(loader)
        repo.seedIfEmpty()

        assertEquals("Bench Press", repo.getById("bench")?.name)
        assertNull(repo.getById("missing"))

        val map = repo.getByIds(listOf("bench", "squat", "missing", "bench"))
        assertEquals(2, map.size)
        assertNotNull(map["bench"])
        assertNotNull(map["squat"])
        assertNull(map["missing"])

        assertTrue(repo.getByIds(emptyList()).isEmpty())
    }

    @Test
    fun `getAllCategories returns distinct sorted categories`() = runTest {
        val loader = FakeLoader(
            ApplicationProvider.getApplicationContext(),
            Result.success(
                listOf(
                    entity("a", "A", "strength"),
                    entity("b", "B", "cardio"),
                    entity("c", "C", "strength"),
                )
            )
        )
        val repo = repo(loader)
        repo.seedIfEmpty()

        assertEquals(listOf("cardio", "strength"), repo.getAllCategories())
    }
}
