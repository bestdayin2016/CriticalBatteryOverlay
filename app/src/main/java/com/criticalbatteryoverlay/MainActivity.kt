package com.criticalbatteryoverlay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criticalbatteryoverlay.data.BatteryPreferences
import com.criticalbatteryoverlay.service.BatteryForegroundService
import com.criticalbatteryoverlay.ui.theme.AlertRed
import com.criticalbatteryoverlay.ui.theme.CriticalBatteryTheme
import com.criticalbatteryoverlay.ui.theme.DarkBackground
import com.criticalbatteryoverlay.ui.theme.DarkSurface
import com.criticalbatteryoverlay.ui.theme.DarkSurfaceVariant
import com.criticalbatteryoverlay.ui.theme.MutedGray
import com.criticalbatteryoverlay.ui.theme.PureWhite
import com.criticalbatteryoverlay.ui.theme.SuccessGreen
import com.criticalbatteryoverlay.ui.theme.WarningYellow
import com.criticalbatteryoverlay.util.PermissionUtils
import com.criticalbatteryoverlay.util.RedOverlayManager
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var preferences: BatteryPreferences
    private var canDrawOverlaysState = mutableStateOf(false)
    private var hasNotificationsState = mutableStateOf(false)
    private var isIgnoringBatteryState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = BatteryPreferences(this)

        if (preferences.isServiceEnabled) {
            BatteryForegroundService.startService(this)
        }

        setContent {
            CriticalBatteryTheme {
                MainScreen(
                    preferences = preferences,
                    canDrawOverlays = canDrawOverlaysState.value,
                    hasNotifications = hasNotificationsState.value,
                    isIgnoringBattery = isIgnoringBatteryState.value,
                    onOpenOverlaySettings = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    },
                    onRequestBatteryUnrestricted = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        canDrawOverlaysState.value = PermissionUtils.canDrawOverlays(this)
        hasNotificationsState.value = PermissionUtils.hasNotificationPermission(this)
        isIgnoringBatteryState.value = PermissionUtils.isIgnoringBatteryOptimizations(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    preferences: BatteryPreferences,
    canDrawOverlays: Boolean,
    hasNotifications: Boolean,
    isIgnoringBattery: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit
) {
    val context = LocalContext.current

    var isServiceEnabled by remember { mutableStateOf(preferences.isServiceEnabled) }
    var batteryThreshold by remember { mutableIntStateOf(preferences.batteryThreshold) }
    var overlayOpacity by remember { mutableFloatStateOf(preferences.overlayOpacity) }

    var currentBatteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var isTestingOverlay by remember { mutableStateOf(false) }

    // Notification launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Refresh in onResume */ }
    )

    // Dynamic battery receiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(cntx: Context?, intent: Intent?) {
                if (intent == null || intent.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    currentBatteryLevel = (level * 100) / scale
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                currentBatteryLevel = (level * 100) / scale
            }
            val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val isOverlayCurrentlyActive = RedOverlayManager.isOverlayActive() ||
            (isServiceEnabled && currentBatteryLevel <= batteryThreshold && !isCharging && canDrawOverlays)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CriticalBatteryOverlay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = PureWhite
                        )
                        Text(
                            text = "Low Battery Emergency Overlay",
                            fontSize = 12.sp,
                            color = MutedGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Battery: $currentBatteryLevel%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isCharging) "Charging (Overlay Paused)" else "Discharging",
                                fontSize = 13.sp,
                                color = if (isCharging) SuccessGreen else MutedGray
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isOverlayCurrentlyActive) AlertRed else DarkSurfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isOverlayCurrentlyActive) "OVERLAY ACTIVE" else "INACTIVE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = PureWhite
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Master Service Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Battery Monitor",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Runs low-power background monitor to trigger red overlay",
                            fontSize = 12.sp,
                            color = MutedGray
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = { checked ->
                            isServiceEnabled = checked
                            preferences.isServiceEnabled = checked
                            if (checked) {
                                BatteryForegroundService.startService(context)
                            } else {
                                BatteryForegroundService.stopService(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = AlertRed,
                            uncheckedThumbColor = MutedGray,
                            uncheckedTrackColor = DarkBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Configuration Sliders Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Overlay Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PureWhite
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Threshold Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Trigger Threshold", fontSize = 13.sp, color = PureWhite)
                        Text(
                            text = "$batteryThreshold%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AlertRed
                        )
                    }
                    Slider(
                        value = batteryThreshold.toFloat(),
                        onValueChange = {
                            batteryThreshold = it.roundToInt()
                            preferences.batteryThreshold = batteryThreshold
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = AlertRed,
                            activeTrackColor = AlertRed,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Red Overlay Opacity", fontSize = 13.sp, color = PureWhite)
                        Text(
                            text = "${(overlayOpacity * 100).roundToInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AlertRed
                        )
                    }
                    Slider(
                        value = overlayOpacity,
                        onValueChange = {
                            overlayOpacity = it
                            preferences.overlayOpacity = overlayOpacity
                            if (RedOverlayManager.isOverlayActive()) {
                                RedOverlayManager.updateOpacity(overlayOpacity)
                            }
                        },
                        valueRange = 0.10f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = AlertRed,
                            activeTrackColor = AlertRed,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Preview Button
                    Button(
                        onClick = {
                            if (canDrawOverlays) {
                                isTestingOverlay = true
                                RedOverlayManager.showOverlay(context, overlayOpacity)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    RedOverlayManager.hideOverlay(context)
                                    isTestingOverlay = false
                                }, 3000L)
                            } else {
                                onOpenOverlaySettings()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Text(
                            text = if (isTestingOverlay) "Testing Red Overlay (3s)..." else "Test Red Overlay Preview (3s)",
                            fontSize = 13.sp,
                            color = PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Permission Status Cards
            Text(
                text = "Required Permissions",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = PureWhite
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status Card 1: System Overlay
            StatusCard(
                title = "Draw Over Other Apps",
                statusText = if (canDrawOverlays) "Granted" else "Required",
                isSuccess = canDrawOverlays,
                actionText = if (canDrawOverlays) "Granted" else "Grant Permission",
                onActionClick = onOpenOverlaySettings
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status Card 2: Notifications (Android 13+)
            StatusCard(
                title = "Post Notifications",
                statusText = if (hasNotifications) "Granted" else "Required for Service",
                isSuccess = hasNotifications,
                actionText = if (hasNotifications) "Granted" else "Grant Permission",
                onActionClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status Card 3: Battery Optimization
            StatusCard(
                title = "Unrestricted Battery Usage",
                statusText = if (isIgnoringBattery) "Unrestricted" else "Optimized (May delay overlay)",
                isSuccess = isIgnoringBattery,
                actionText = if (isIgnoringBattery) "Unrestricted" else "Disable Optimization",
                onActionClick = onRequestBatteryUnrestricted
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    statusText: String,
    isSuccess: Boolean,
    actionText: String,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isSuccess) SuccessGreen else WarningYellow)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PureWhite
                    )
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = if (isSuccess) SuccessGreen else WarningYellow
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = actionText, fontSize = 11.sp, color = PureWhite)
            }
        }
    }
}
