package com.hellohealth.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hellohealth.data.local.entities.CachedFoodEntity
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import kotlin.math.roundToInt

/**
 * The full Nutrition screen: the day's food log grouped into meal sections (each with a subtotal),
 * a water row, a debounced food-search typeahead (local cache + USDA/OFF), a quick-add path, and a
 * barcode CTA. Tapping an entry soft-deletes it. Fed by [NutritionScreenViewModel]; every write is
 * fire-and-forget and the day summary re-emits on its own. Mood-tinted like the other full screens.
 *
 * [onScanBarcode] opens the camera scanner; the scanned raw barcode is delivered back via the nav
 * result and surfaces here through [scannedBarcode]. It is resolved once (cache → OFF → cache) and,
 * if a product is found, opens the add sheet pre-filled with it; an unresolved scan is consumed
 * silently (the user can fall back to search / quick-add).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionScreenViewModel,
    onBack: () -> Unit,
    onScanBarcode: () -> Unit,
    onOpenSettings: () -> Unit = {},
    scannedBarcode: String? = null,
    onScannedBarcodeConsumed: () -> Unit = {},
) {
    val summary by viewModel.daySummary.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val caloriesOut by viewModel.caloriesOut.collectAsState()
    val query by viewModel.query.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    // Add-sheet target: a resolved catalog food OR null for the bare quick-add sheet.
    var addSheetFood by remember { mutableStateOf<CachedFoodEntity?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }
    // The full-screen food-search overlay (opened by tapping the search field on the main page).
    var showSearch by rememberSaveable { mutableStateOf(false) }

    // A barcode scan returns the raw code; resolve it (cache → OFF → cache) and, on a hit, open the
    // add sheet pre-filled. Consumed exactly once so a config change doesn't re-open the sheet.
    androidx.compose.runtime.LaunchedEffect(scannedBarcode) {
        val code = scannedBarcode ?: return@LaunchedEffect
        val food = viewModel.resolveBarcode(code)
        if (food != null) addSheetFood = food
        onScannedBarcodeConsumed()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Nutrition", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Nutrition settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.15f), backgroundColor)
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item { CalorieHeroCard(summary, targets, caloriesOut) }

                // Search as its own distinct block. Tapping the field opens the full-screen search
                // overlay (results no longer reflow this page); Scan + Quick-add stay inline.
                item {
                    SearchBlock(
                        onOpenSearch = { showSearch = true },
                        onScan = onScanBarcode,
                        onQuickAdd = { showQuickAdd = true },
                    )
                }

                item { MicronutrientsCard(summary, targets) }

                // Meal sections.
                items(MealCategory.entries.sortedBy { it.order }, key = { it.name }) { meal ->
                    MealSection(
                        meal = meal,
                        entries = summary.entriesByMeal[meal].orEmpty(),
                        onDelete = viewModel::deleteEntry,
                    )
                }

                // Water.
                item {
                    WaterRow(
                        waterMl = summary.waterMl,
                        onAddGlass = { viewModel.addWater() },
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Add-from-catalog sheet.
    addSheetFood?.let { food ->
        AddFoodSheet(
            food = food,
            onDismiss = { addSheetFood = null },
            onConfirm = { meal, quantity, unit ->
                viewModel.addFromFood(meal, food.id, quantity, unit)
                addSheetFood = null
            },
        )
    }

    // Quick-add sheet.
    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onConfirm = { meal, name, calories, protein, carbs, fat, fibre ->
                viewModel.addQuickAdd(
                    meal = meal,
                    foodName = name,
                    quantity = 1.0,
                    unit = "serving",
                    calories = calories,
                    proteinG = protein,
                    carbsG = carbs,
                    fatG = fat,
                    fibreG = fibre,
                )
                showQuickAdd = false
            },
        )
    }

    // Full-screen food-search overlay. Owns its own focused field + result list so the main page
    // never reflows or pops a keyboard. Dismiss (back/close) clears the query.
    if (showSearch) {
        FoodSearchOverlay(
            query = query,
            state = searchState,
            onQueryChange = viewModel::onQueryChange,
            onDismiss = {
                viewModel.clearSearch()
                showSearch = false
            },
            onPick = { food ->
                addSheetFood = food
                viewModel.clearSearch()
                showSearch = false
            },
        )
    }
}

/**
 * Full-screen search surface shown over the Nutrition screen. A top bar with a back arrow + an
 * auto-focused text field drives the shared VM query; the body renders a loader / empty / no-results
 * / ranked-results state from [SearchState]. Reuses [SearchResultRow] for each hit. Implemented as a
 * full-bleed [Dialog] (no nav route needed); back and the close arrow both dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSearchOverlay(
    query: String,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (CachedFoodEntity) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search foods…") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                )

                when (val s = state) {
                    is SearchState.Idle -> SearchHint("Search for a food to log it.")
                    is SearchState.Loading -> SearchLoading()
                    is SearchState.Empty -> SearchHint("No foods found for “$query”.\nTry another spelling or Quick add.")
                    is SearchState.Results -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(s.foods, key = { it.id }) { food ->
                            SearchResultRow(food = food, onClick = { onPick(food) })
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

/** Centered loader for the search overlay while a query is in flight. */
@Composable
private fun SearchLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Centered hint/empty text for the search overlay (idle prompt or no-results message). */
@Composable
private fun SearchHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * The hero card: a calories-ONLY ring (eaten-led — center shows eaten big + "of <target> kcal"), side
 * stats (remaining / burned / % of goal), then four full-word macro bars with distinct colors showing
 * `consumed / target g`. An over-target macro bar turns red so over-consumption reads visually without
 * extra text. Targets come from the resolver (profile derivation + the user's overrides).
 */
@Composable
private fun CalorieHeroCard(
    summary: NutritionDaySummary,
    targets: com.hellohealth.domain.health.NutritionTargets.ResolvedTargets?,
    caloriesOut: Double?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val eaten = summary.caloriesConsumed.roundToInt()
    val budget = targets?.calorieBudget
    val remaining = budget?.let { it - eaten }
    val progress = if (budget != null && budget > 0) (eaten.toFloat() / budget).coerceIn(0f, 1f) else 0f

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 13.dp.toPx()
                        val d = size.minDimension - stroke
                        val tl = androidx.compose.ui.geometry.Offset((size.width - d) / 2f, (size.height - d) / 2f)
                        val arc = androidx.compose.ui.geometry.Size(d, d)
                        drawArc(onSurface.copy(alpha = 0.08f), 0f, 360f, false, tl, arc,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        drawArc(primary, -90f, 360f * progress, false, tl, arc,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$eaten", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = onSurface)
                        Text(if (budget != null) "of $budget kcal" else "kcal eaten",
                            style = MaterialTheme.typography.labelMedium, color = onSurface.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    HeroStat(remaining?.let { "$it" } ?: "—", "kcal remaining", if ((remaining ?: 0) < 0) MaterialTheme.colorScheme.error else primary)
                    Spacer(Modifier.height(12.dp))
                    HeroStat(caloriesOut?.roundToInt()?.toString() ?: "—", "kcal burned", onSurface)
                    Spacer(Modifier.height(12.dp))
                    HeroStat(if (budget != null) "${(progress * 100).roundToInt()}%" else "—", "of daily goal", onSurface)
                }
            }

            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.HorizontalDivider(color = onSurface.copy(alpha = 0.07f))
            Spacer(Modifier.height(16.dp))

            MacroBar("Protein", summary.proteinG, targets?.proteinG, Color(0xFF5B8DEF))
            MacroBar("Carbs", summary.carbsG, targets?.carbsG, Color(0xFFF2A33C))
            MacroBar("Fat", summary.fatG, targets?.fatG, Color(0xFFEF6D6D))
            MacroBar("Fiber", summary.fibreG, targets?.fiberG, Color(0xFF66BB6A), last = true)
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, valueColor: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

/** A full-word macro bar: `Name   consumed / target g`, colored fill; over-target → red fill. */
@Composable
private fun MacroBar(name: String, consumed: Double, target: Int?, color: Color, last: Boolean = false) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val warn = MaterialTheme.colorScheme.error
    val c = consumed.roundToInt()
    val over = target != null && c > target
    val fraction = if (target != null && target > 0) (c.toFloat() / target).coerceIn(0f, 1f) else 0f
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
            Text(
                buildString { append(c); append(" / "); append(target?.toString() ?: "—"); append(" g") },
                style = MaterialTheme.typography.bodySmall, color = onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.09f))) {
            Box(modifier = Modifier.fillMaxWidth(fraction).height(9.dp).clip(RoundedCornerShape(6.dp)).background(if (over) warn else color))
        }
        if (!last) Spacer(Modifier.height(14.dp))
    }
}

/**
 * Search as its own distinct block (separated from the day's info): a tap-to-open search trigger plus
 * Scan and Quick-add affordances. Tapping the trigger opens the full-screen [FoodSearchOverlay] — the
 * field here is a non-editable affordance, so the keyboard never pops inline on the main page.
 */
@Composable
private fun SearchBlock(
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
    onQuickAdd: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            SearchTrigger(onClick = onOpenSearch)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Scan")
                }
                FilledTonalButton(onClick = onQuickAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Quick add")
                }
            }
        }
    }
}

/** A read-only, search-bar-shaped row that opens the full-screen search overlay when tapped. */
@Composable
private fun SearchTrigger(onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.width(12.dp))
        Text("Search foods…", color = onSurface.copy(alpha = 0.6f))
    }
}

/**
 * Collapsible "Micronutrients" breakdown — each curated nutrient vs its FDA Daily Value, shown as a
 * progress bar with the consumed/target amounts. A nutrient no food carried shows "— no data" (never a
 * fake 0). Limit nutrients (sodium/saturated fat/sugar) are framed as ceilings to stay under. This is a
 * reference view, not medical advice.
 */
@Composable
private fun MicronutrientsCard(
    summary: NutritionDaySummary,
    targets: com.hellohealth.domain.health.NutritionTargets.ResolvedTargets?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Only the micronutrients the user chose to track (settings); default is all 8.
    val shown = com.hellohealth.domain.health.MicronutrientTargets.Micro.entries
        .filter { targets?.trackedMicros?.contains(it) ?: true }
    val trackedCount = shown.count { summary.micros.containsKey(it) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Micronutrients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(
                        if (trackedCount > 0) "$trackedCount of ${shown.size} tracked today · vs daily value" else "Log foods to see vitamins & minerals",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface.copy(alpha = 0.55f),
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = onSurface.copy(alpha = 0.6f),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                shown.forEach { micro ->
                    MicronutrientRow(micro, summary.micros[micro])
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "Reference daily values (FDA), not medical advice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun MicronutrientRow(micro: com.hellohealth.domain.health.MicronutrientTargets.Micro, consumed: Double?) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val warn = Color(0xFFEF5350)
    val target = com.hellohealth.domain.health.MicronutrientTargets.targetFor(micro)
    val fraction = consumed?.let { (it / target).toFloat().coerceIn(0f, 1f) } ?: 0f
    // Limit nutrients (sodium/sat fat/sugar) turn red as they approach the ceiling; goals use the accent.
    val barColor = if (micro.isLimit && fraction >= 0.8f) warn else primary
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(micro.label, style = MaterialTheme.typography.bodyMedium, color = onSurface, modifier = Modifier.weight(1f))
            Text(
                if (consumed != null) "${consumed.roundToInt()} / ${target.roundToInt()} ${micro.unit}${if (micro.isLimit) " max" else ""}"
                else "— no data",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (consumed != null) onSurface else onSurface.copy(alpha = 0.4f),
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = onSurface.copy(alpha = 0.08f),
        )
    }
}

@Composable
private fun SearchResultRow(food: CachedFoodEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                val basis = if (food.basisUnit == "per_100g") "per 100 g" else (food.servingLabel ?: "per serving")
                Text(
                    "${food.caloriesPer.roundToInt()} kcal · $basis" + (food.brand?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MealSection(meal: MealCategory, entries: List<FoodEntry>, onDelete: (String) -> Unit) {
    val subtotal = entries.sumOf { it.calories }.roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meal.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (entries.isEmpty()) "—" else "$subtotal kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (entries.isEmpty()) {
            Text(
                "No items yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        } else {
            entries.forEach { entry -> FoodEntryRow(entry = entry, onDelete = { onDelete(entry.id) }) }
        }
    }
}

@Composable
private fun FoodEntryRow(entry: FoodEntry, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.foodName, fontWeight = FontWeight.Medium)
                Text(
                    "${entry.calories.roundToInt()} kcal · ${entry.quantity.roundToInt()} ${entry.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WaterRow(waterMl: Double, onAddGlass: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocalDrink, contentDescription = null, tint = Color(0xFF26C6DA))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Water", fontWeight = FontWeight.Bold)
                Text(
                    if (waterMl <= 0) "—" else if (waterMl >= 1000) String.format("%.1f L", waterMl / 1000) else "${waterMl.roundToInt()} ml",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            FilledTonalButton(onClick = onAddGlass) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("250 ml")
            }
        }
    }
}

/** Sheet to log a catalog food: pick a meal + quantity, unit implied by the food's basis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodSheet(
    food: CachedFoodEntity,
    onDismiss: () -> Unit,
    onConfirm: (MealCategory, Double, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val perServing = food.basisUnit != "per_100g"
    // Default quantity: 1 serving, or 100 g for a per-100g food (so calories map 1:1 to the label).
    var quantityText by remember { mutableStateOf(if (perServing) "1" else "100") }
    var meal by remember { mutableStateOf(MealCategory.BREAKFAST) }
    val unit = if (perServing) "serving" else "g"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            MealChips(selected = meal, onSelect = { meal = it })
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(if (perServing) "Servings" else "Grams") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = {
                    val qty = quantityText.toDoubleOrNull() ?: return@FilledTonalButton
                    if (qty > 0) onConfirm(meal, qty, unit)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add") }
        }
    }
}

/** Bare quick-add: name + calories, optional macros, under a meal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(
    onDismiss: () -> Unit,
    onConfirm: (MealCategory, String, Double, Double?, Double?, Double?, Double?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var fibreText by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(MealCategory.BREAKFAST) }

    fun num(s: String) = s.toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            MealChips(selected = meal, onSelect = { meal = it })
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Food name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = caloriesText,
                onValueChange = { caloriesText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Calories (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = proteinText,
                    onValueChange = { proteinText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("P (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("C (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("F (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fibreText,
                    onValueChange = { fibreText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Fiber (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            FilledTonalButton(
                onClick = {
                    val calories = num(caloriesText) ?: return@FilledTonalButton
                    if (name.isBlank() || calories <= 0) return@FilledTonalButton
                    onConfirm(meal, name.trim(), calories, num(proteinText), num(carbsText), num(fatText), num(fibreText))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add") }
        }
    }
}

@Composable
private fun MealChips(selected: MealCategory, onSelect: (MealCategory) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealCategory.entries.sortedBy { it.order }.forEach { meal ->
            val isSelected = meal == selected
            Text(
                text = meal.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(meal) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
