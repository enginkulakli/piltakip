package com.vahsi.piltakip

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PilTakipApp() }
    }
}

private fun formatDateTime(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(time))

private fun nextDate(time: Long, days: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = time
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

private fun loadHistories(prefs: android.content.SharedPreferences): Map<String, List<Long>> {
    val result = mutableMapOf<String, List<Long>>()
    val saved = prefs.getString("histories_by_type", null)
    if (saved != null) {
        runCatching {
            val obj = JSONObject(saved)
            obj.keys().forEach { key ->
                val arr = obj.optJSONArray(key) ?: JSONArray()
                result[key] = (0 until arr.length()).mapNotNull { i ->
                    arr.optLong(i).takeIf { it > 0L }
                }.distinct().sortedDescending()
            }
        }
    } else {
        // Önceki sürümdeki kayıtları kaybetmeden varsayılan kategoriye aktar.
        val old = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrNull() ?: JSONArray()
        val records = (0 until old.length()).mapNotNull { i ->
            old.optLong(i).takeIf { it > 0L }
        }.distinct().sortedDescending()
        if (records.isNotEmpty()) result["Sensör pili"] = records
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilTakipApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("pil_takip", 0) }
    var interval by remember { mutableIntStateOf(prefs.getInt("interval", 28)) }
    var categories by remember {
        mutableStateOf(
            run {
                val arr = runCatching { JSONArray(prefs.getString("categories", null)) }.getOrNull()
                if (arr == null) listOf("Sensör pili", "Pompa pili", "Saat pili")
                else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                    .ifEmpty { listOf("Sensör pili", "Pompa pili", "Saat pili") }
            }
        )
    }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var histories by remember { mutableStateOf(loadHistories(prefs)) }
    var entryDate by remember { mutableStateOf<Long?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var categoryInput by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var intervalInput by remember { mutableStateOf(interval.toString()) }

    fun saveHistories(updated: Map<String, List<Long>>) {
        histories = updated
        val obj = JSONObject()
        updated.forEach { (key, values) -> obj.put(key, JSONArray(values.distinct().sortedDescending())) }
        prefs.edit().putString("histories_by_type", obj.toString()).apply()
    }

    fun saveCategories(updated: List<String>) {
        categories = updated
        prefs.edit().putString("categories", JSONArray(updated).toString()).apply()
    }

    fun addRecord(time: Long) {
        val updated = histories.toMutableMap()
        updated[selectedCategory] = (listOf(time) + (histories[selectedCategory] ?: emptyList()))
            .distinct().sortedDescending()
        saveHistories(updated)
    }

    fun chooseDateTime(initial: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                timeInMillis = initial
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(context, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                onPicked(selected.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pil Takip", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = {
                            intervalInput = interval.toString()
                            showSettings = true
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { showCategoryMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedCategory, modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    entryDate = null
                                    showCategoryMenu = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("＋ Yeni pil türü ekle") },
                            onClick = {
                                showCategoryMenu = false
                                categoryInput = ""
                                showAddCategory = true
                            }
                        )
                    }
                }

                Text("DEĞİŞİM TARİHLERİ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Değişim Tarihi / Saat", modifier = Modifier.weight(1.15f), fontWeight = FontWeight.SemiBold)
                    Text("Sonraki Değişim", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(68.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.15f).clickable {
                                chooseDateTime(entryDate ?: System.currentTimeMillis()) { entryDate = it }
                            }.padding(vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text(entryDate?.let { formatDateTime(it) } ?: "Tarih ve saat seç", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(entryDate?.let { formatDateTime(nextDate(it, interval)) } ?: "—", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                entryDate?.let { addRecord(it); entryDate = null }
                            },
                            enabled = entryDate != null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Ekle")
                        }
                    }
                }

                Text("GEÇMİŞ KAYITLAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val selectedHistory = histories[selectedCategory] ?: emptyList()
                if (selectedHistory.isEmpty()) {
                    Text("Henüz kayıt yok.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedHistory) { record ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1.15f).clickable {
                                            chooseDateTime(record) { picked ->
                                                val updated = (histories[selectedCategory] ?: emptyList())
                                                    .filterNot { it == record } + picked
                                                val all = histories.toMutableMap()
                                                all[selectedCategory] = updated.distinct().sortedDescending()
                                                saveHistories(all)
                                            }
                                        }
                                    ) {
                                        Text(formatDateTime(record), fontWeight = FontWeight.SemiBold)
                                        Text("Değişim tarihi", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f).clickable {
                                            val all = histories.toMutableMap()
                                            all[selectedCategory] = (listOf(nextDate(record, interval)) + selectedHistory)
                                                .distinct().sortedDescending()
                                            saveHistories(all)
                                        }
                                    ) {
                                        Text(formatDateTime(nextDate(record, interval)), fontWeight = FontWeight.SemiBold)
                                        Text("Dokun: bu tarihte değiştir", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddCategory) {
            AlertDialog(
                onDismissRequest = { showAddCategory = false },
                title = { Text("Pil türü ekle") },
                text = {
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Örn. Kumanda pili") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = categoryInput.trim()
                        if (name.isNotEmpty() && categories.none { it.equals(name, true) }) {
                            saveCategories(categories + name)
                            selectedCategory = name
                            entryDate = null
                        }
                        showAddCategory = false
                    }) { Text("Ekle") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategory = false }) { Text("İptal") }
                }
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Değişim aralığı") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bir sonraki değişim kaç gün sonra olsun?")
                        OutlinedTextField(
                            value = intervalInput,
                            onValueChange = { intervalInput = it.filter(Char::isDigit).take(3) },
                            label = { Text("Gün") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = intervalInput.toIntOrNull()
                        if (value != null && value in 1..365) {
                            interval = value
                            prefs.edit().putInt("interval", value).apply()
                            showSettings = false
                        }
                    }) { Text("Kaydet") }
                },
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) { Text("İptal") }
                }
            )
        }
    }
}
