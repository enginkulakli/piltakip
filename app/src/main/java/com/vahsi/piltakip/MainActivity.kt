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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PilTakipApp() }
    }
}

private fun formatDate(time: Long, pattern: String = "dd.MM.yyyy HH:mm"): String =
    SimpleDateFormat(pattern, Locale("tr", "TR")).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilTakipApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pil_takip", 0) }
    var interval by remember { mutableIntStateOf(prefs.getInt("interval", 28)) }
    var history by remember {
        mutableStateOf(run {
            val array = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrNull() ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> array.optLong(i).takeIf { it > 0L } }
                .distinct().sortedDescending()
        })
    }
    var showRecordOptions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var intervalInput by remember { mutableStateOf(interval.toString()) }

    fun saveHistory(records: List<Long>) {
        val cleaned = records.distinct().sortedDescending()
        history = cleaned
        prefs.edit().putString("history", JSONArray(cleaned).toString()).apply()
    }

    fun chooseDateTime(initial: Long, onPicked: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, year, month, day ->
            val selected = java.util.Calendar.getInstance().apply {
                timeInMillis = initial
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month)
                set(java.util.Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(context, { _, hour, minute ->
                selected.set(java.util.Calendar.HOUR_OF_DAY, hour)
                selected.set(java.util.Calendar.MINUTE, minute)
                selected.set(java.util.Calendar.SECOND, 0)
                selected.set(java.util.Calendar.MILLISECOND, 0)
                onPicked(selected.timeInMillis)
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    val latest = history.firstOrNull()
    val nextChange = latest?.let { it + interval.toLong() * 24L * 60L * 60L * 1000L }

    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Pil Takip", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { intervalInput = interval.toString(); showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                }
            )
        }) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("SON PİL DEĞİŞİMİ", style = MaterialTheme.typography.labelLarge)
                            Text(latest?.let { formatDate(it) } ?: "Henüz kayıt yok",
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (latest == null) saveHistory(listOf(System.currentTimeMillis()))
                                        else showRecordOptions = true
                                    }
                                ) { Text(if (latest == null) "Pil değişimini işaretle" else "Değişim kaydına dokun") }
                                OutlinedButton(onClick = {
                                    chooseDateTime(latest ?: System.currentTimeMillis()) { picked ->
                                        if (latest == null) saveHistory(listOf(picked))
                                        else saveHistory(listOf(picked) + history.drop(1))
                                    }
                                }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Takvimden tarih seç")
                                }
                            }
                            Text(if (latest == null) "İlk dokunuşta tarih ve saat otomatik kaydedilir."
                                else "Tekrar dokunarak tarihi düzelt veya yeni değişim kaydı ekle.")
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("SONRAKİ DEĞİŞİM", style = MaterialTheme.typography.labelLarge)
                            Text(nextChange?.let { formatDate(it, "dd MMMM yyyy") } ?: "Kayıt bekleniyor",
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(if (latest == null) "Önce ilk değişim kaydını oluştur." else "$interval gün aralıkla hesaplanır.")
                        }
                    }
                }
                item { Text("GEÇMİŞ KAYITLAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (history.isEmpty()) item { Text("Henüz geçmiş kaydı bulunmuyor.") }
                else items(history) { record ->
                    ListItem(
                        headlineContent = { Text(formatDate(record)) },
                        supportingContent = { Text("Düzenlemek için dokun") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.clickable {
                            chooseDateTime(record) { picked ->
                                val updated = history.toMutableList()
                                val index = updated.indexOf(record)
                                if (index >= 0) updated[index] = picked
                                saveHistory(updated)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }

        if (showRecordOptions) {
            AlertDialog(
                onDismissRequest = { showRecordOptions = false },
                title = { Text("Pil değişim kaydı") },
                text = { Text("Mevcut kaydı düzeltmek mi, yeni bir değişim kaydı eklemek mi istiyorsun?") },
                confirmButton = {
                    TextButton(onClick = {
                        showRecordOptions = false
                        chooseDateTime(latest ?: System.currentTimeMillis()) { picked ->
                            saveHistory(listOf(picked) + history.drop(1))
                        }
                    }) { Text("Tarihi düzelt") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showRecordOptions = false
                            saveHistory(listOf(System.currentTimeMillis()) + history)
                        }) { Text("Yeni kayıt") }
                        TextButton(onClick = { showRecordOptions = false }) { Text("Vazgeç") }
                    }
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
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("İptal") } }
            )
        }
    }
}
