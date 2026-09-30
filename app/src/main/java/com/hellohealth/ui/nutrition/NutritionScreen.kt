package com.hellohealth.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    scannedBarcode: String? = null,
    onScannedBarcodeConsumed: () -> Unit = {},
) {
    val summary by viewModel.daySummary.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    // Add-sheet target: a resolved catalog food OR null for the bare quick-add sheet.
    var addSheetFood by remember { mutableStateOf<CachedFoodEntity?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onScanBarcode) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
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

                item { DayTotalsCard(summary) }

                // Search box + typeahead results.
                item {
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        onClear = viewModel::clearSearch,
                    )
                }
                if (results.isNotEmpty()) {
                    items(results, key = { it.id }) { food ->
                        SearchResultRow(food = food, onClick = { addSheetFood = food })
                    }
                }

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

                // Quick-add entry point.
                item {
                    TextButton(onClick = { showQuickAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quick add (name + calories)")
                    }
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
            onConfirm = { meal, name, calories, protein, carbs, fat ->
                viewModel.addQuickAdd(
                    meal = meal,
                    foodName = name,
                    quantity = 1.0,
                    unit = "serving",
                    calories = calories,
                    proteinG = protein,
                    carbsG = carbs,
                    fatG = fat,
                )
                showQuickAdd = false
            },
        )
    }
}

@Composable
private fun DayTotalsCard(summary: NutritionDaySummary) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${summary.caloriesConsumed.roundToInt()} kcal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MacroPill("P", summary.proteinG)
                MacroPill("C", summary.carbsG)
                MacroPill("F", summary.fatG)
            }
        }
    }
}

@Composable
private fun MacroPill(label: String, grams: Double) {
    Text(
        text = "$label ${grams.roundToInt()}g",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search foods…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
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
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    onConfirm: (MealCategory, String, Double, Double?, Double?, Double?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(MealCategory.BREAKFAST) }

    fun num(s: String) = s.toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
            FilledTonalButton(
                onClick = {
                    val calories = num(caloriesText) ?: return@FilledTonalButton
                    if (name.isBlank() || calories <= 0) return@FilledTonalButton
                    onConfirm(meal, name.trim(), calories, num(proteinText), num(carbsText), num(fatText))
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
