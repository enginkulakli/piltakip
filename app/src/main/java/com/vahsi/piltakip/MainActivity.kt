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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
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

private fun formatDate(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).format(Date(time))

private fun nextDate(time: Long, days: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = time
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilTakipApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("pil_takip", 0) }
    var interval by remember { mutableIntStateOf(prefs.getInt("interval", 28)) }
    var history by remember {
        mutableStateOf(run {
            val array = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrNull() ?: JSONArray()
            (0 until array.length()).mapNotNull { i -> array.optLong(i).takeIf { it > 0L } }
                .distinct().sortedDescending()
        })
    }
    var showSettings by remember { mutableStateOf(false) }
    var intervalInput by remember { mutableStateOf(interval.toString()) }

    fun saveHistory(records: List<Long>) {
        val cleaned = records.distinct().sortedDescending()
        history = cleaned
        prefs.edit().putString("history", JSONArray(cleaned).toString()).apply()
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
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)
            ) {
                Text(
                    "DEĞİŞİM TARİHLERİ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Değişim Tarihi",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Sonraki Değişim",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        // Boş giriş satırına dokununca tarih/saat anında kaydedilir.
                        saveHistory(listOf(System.currentTimeMillis()) + history)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Text("Tarih ekle", fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Otomatik hesaplanır", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "GEÇMİŞ KAYITLAR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                if (history.isEmpty()) {
                    Text("Henüz kayıt yok. Yukarıdaki satıra dokunarak ilk tarihi ekle.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { record ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f).clickable {
                                            chooseDateTime(record) { picked ->
                                                val updated = history.toMutableList()
                                                val index = updated.indexOf(record)
                                                if (index >= 0) updated[index] = picked
                                                saveHistory(updated)
                                            }
                                        }
                                    ) {
                                        Text(formatDate(record), fontWeight = FontWeight.SemiBold)
                                        Text("Değişim tarihi", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f).clickable {
                                            val scheduled = nextDate(record, interval)
                                            saveHistory(listOf(scheduled) + history)
                                        }
                                    ) {
                                        Text(formatDate(nextDate(record, interval)), fontWeight = FontWeight.SemiBold)
                                        Text("Sonraki değişim", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
