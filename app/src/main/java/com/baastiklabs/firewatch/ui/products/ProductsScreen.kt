package com.baastiklabs.firewatch.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.Absorption
import com.baastiklabs.firewatch.core.toDose
import com.baastiklabs.firewatch.core.model.DefaultProducts
import com.baastiklabs.firewatch.core.model.Product
import com.baastiklabs.firewatch.core.model.ProductKind
import com.baastiklabs.firewatch.core.model.SpeedProfile
import com.baastiklabs.firewatch.core.records.FirewatchData
import com.baastiklabs.firewatch.ui.FirewatchViewModel
import com.baastiklabs.firewatch.ui.Fmt
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(vm: FirewatchViewModel, data: FirewatchData, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Product?>(null) }
    var adding by remember { mutableStateOf(false) }
    val products = data.products.filter { !it.archived }.sortedWith(compareBy({ it.order }, { it.name }))

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Products") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    "Switch on the ones you use regularly to get a big button on the home screen. Tap one to edit its estimate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(products, key = { it.id }) { product ->
                val pieces = Absorption.pieces(Absorption.absorbedMg(product), data.referenceMg)
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { editing = product },
                    headlineContent = { Text(product.name) },
                    supportingContent = {
                        Text(
                            "${DefaultProducts.kindLabel(product.kind)} · ${formatMg(product.labelMg)} mg · " +
                                "~${(product.absorption * 100).roundToInt()}% absorbed · ${Fmt.piecesLabel(pieces)}\n" +
                                "Quality ${com.baastiklabs.firewatch.core.engine.Quality.label(com.baastiklabs.firewatch.core.engine.Quality.doseScore(product.toDose("q", 0, 0), data.referenceMg, false))}",
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = product.onHome,
                            onCheckedChange = { on -> scope.launch { vm.repository.saveProduct(product.copy(onHome = on)) } },
                        )
                    },
                )
            }
            item {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Add a product")
                }
            }
            item {
                Text(
                    "Absorption is how much of the label strength actually reaches your blood. Defaults come from published research: " +
                        "gum is commonly cited at about half its label amount, and pouches vary by brand and how long one stays in. " +
                        "Every figure is an estimate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (adding) {
        ProductDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { product ->
                adding = false
                scope.launch {
                    val now = vm.repository.now()
                    vm.repository.saveProduct(
                        product.copy(id = vm.repository.newId(), createdAt = now, order = (products.maxOfOrNull { it.order } ?: 0) + 1),
                    )
                }
            },
            onRemove = null,
        )
    }
    editing?.let { product ->
        ProductDialog(
            initial = product,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch { vm.repository.saveProduct(updated) }
            },
            onRemove = if (product.id == data.settings.referenceProductId) {
                null
            } else {
                {
                    editing = null
                    scope.launch { vm.repository.saveProduct(product.copy(archived = true, onHome = false)) }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductDialog(
    initial: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: ProductKind.POUCH) }
    var mgText by remember { mutableStateOf(initial?.labelMg?.let { formatMg(it) } ?: "") }
    var absorptionText by remember {
        mutableStateOf(((initial?.absorption ?: DefaultProducts.defaultAbsorption(kind)) * 100).roundToInt().toString())
    }
    var absorptionEdited by remember { mutableStateOf(initial != null) }
    var speed by remember { mutableStateOf(initial?.speed ?: DefaultProducts.defaultSpeed(kind)) }
    var priceText by remember { mutableStateOf(initial?.unitPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var packText by remember { mutableStateOf(initial?.unitsPerPack?.takeIf { it > 0 }?.toString() ?: "") }

    val mg = mgText.replace(',', '.').toDoubleOrNull()
    val absorption = absorptionText.toIntOrNull()
    val valid = name.isNotBlank() && mg != null && mg > 0 && absorption != null && absorption in 1..100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a product" else "Edit product") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Type", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProductKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                speed = DefaultProducts.defaultSpeed(k)
                                if (!absorptionEdited) {
                                    absorptionText = (DefaultProducts.defaultAbsorption(k) * 100).roundToInt().toString()
                                }
                            },
                            label = { Text(DefaultProducts.kindLabel(k)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = mgText,
                    onValueChange = { mgText = it },
                    label = { Text("Strength on the packaging (mg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = absorptionText,
                    onValueChange = {
                        absorptionText = it.filter(Char::isDigit).take(3)
                        absorptionEdited = true
                    },
                    label = { Text("Estimated % absorbed") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("How fast it hits", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SpeedProfile.SPIKE to "Spike in minutes",
                        SpeedProfile.BUILD to "Builds over ~30 min",
                        SpeedProfile.FLAT to "Slow and flat",
                    ).forEach { (s, label) ->
                        FilterChip(selected = speed == s, onClick = { speed = s }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price of one (optional, for money saved)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = packText,
                    onValueChange = { packText = it.filter(Char::isDigit).take(4) },
                    label = { Text("How many in a tin/box (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove this product") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val base = initial ?: Product(id = "", name = "")
                    onSave(
                        base.copy(
                            name = name.trim(),
                            kind = kind,
                            labelMg = mg ?: 0.0,
                            absorption = (absorption ?: 50) / 100.0,
                            speed = speed,
                            unitPrice = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                            unitsPerPack = packText.toIntOrNull() ?: 0,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatMg(mg: Double): String =
    if (mg == Math.floor(mg)) mg.toInt().toString() else String.format(Locale.getDefault(), "%.1f", mg)
