package com.example.paymenthelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InstalledApp(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaymentHelperScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentHelperScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("payment_helper", Context.MODE_PRIVATE) }
    var selectedPackage by remember { mutableStateOf(prefs.getString("selected_package", null)) }
    var selectedLabel by remember { mutableStateOf(prefs.getString("selected_label", null)) }
    var showPicker by remember { mutableStateOf(false) }
    var balance by remember { mutableStateOf(prefs.getString("last_balance", null)) }
    var status by remember { mutableStateOf("Выберите банковское приложение.") }
    var busy by remember { mutableStateOf(false) }

    val installedApps = remember {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                InstalledApp(info.loadLabel(pm).toString(), packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun serviceEnabled(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
                   it.resolveInfo.serviceInfo.name == BankAccessibilityService::class.java.name }
    }

    fun startCheck() {
        if (selectedPackage == null) {
            status = "Сначала выберите банковское приложение."
            showPicker = true
            return
        }
        if (!serviceEnabled()) {
            status = "Включите службу доступности Payment Helper в настройках Android."
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedPackage!!)
        if (launchIntent == null) {
            status = "Не удалось открыть выбранное приложение."
            return
        }
        BankAccessibilityService.beginCheck(context, selectedPackage!!)
        busy = true
        balance = null
        status = "Ожидание текста баланса в банковском приложении…"
        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            busy = false
            status = "Не удалось запустить банковское приложение."
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val result = prefs.getString("last_balance", null)
            val timestamp = prefs.getLong("last_balance_time", 0L)
            if (timestamp > 0L && timestamp > prefs.getLong("shown_balance_time", 0L)) {
                balance = result
                prefs.edit().putLong("shown_balance_time", timestamp).apply()
                busy = false
                status = if (result != null) "Баланс прочитан с экрана приложения." else
                    "Баланс не найден. Откройте главный экран банка и повторите попытку."
            }
            delay(500)
        }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Payment Helper") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Банковское приложение", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedLabel ?: "Выбрать приложение")
                }
                Button(
                    onClick = { startCheck() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Проверяем…" else "Проверить баланс")
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("Последний найденный баланс", style = MaterialTheme.typography.titleMedium)
                Text(balance ?: "Пока нет данных", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Данные читаются локально с видимого экрана выбранного приложения. " +
                    "Сервис не нажимает кнопки и не отправляет информацию в интернет.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Настройки службы доступности") }
            }
        }

        if (showPicker) {
            AlertDialog(
                onDismissRequest = { showPicker = false },
                title = { Text("Выберите приложение") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(installedApps, key = { it.packageName }) { app ->
                            TextButton(
                                onClick = {
                                    selectedPackage = app.packageName
                                    selectedLabel = app.label
                                    prefs.edit()
                                        .putString("selected_package", app.packageName)
                                        .putString("selected_label", app.label)
                                        .apply()
                                    showPicker = false
                                    status = "Выбрано: ${app.label}"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(app.label)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPicker = false }) { Text("Закрыть") }
                }
            )
        }
    }
}
