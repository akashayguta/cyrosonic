package com.example.hunterxmusic.presentation.owner

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hunterxmusic.HunterApplication
import com.example.hunterxmusic.data.notification.BroadcastNotificationPayload
import com.example.hunterxmusic.data.remote.AeroChaseCloudSyncManager
import com.example.hunterxmusic.data.remote.CloudAppConfig
import com.example.hunterxmusic.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerAdminPortalView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentCloudConfig by AeroChaseCloudSyncManager.configState.collectAsState()
    val installedVersion = AeroChaseCloudSyncManager.getInstalledAppVersion()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Version Gate & Update, 1: Maintenance, 2: Broadcast, 3: Server & Alert

    // ─── Version & Force Update State ───
    var minRequiredVersion by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.minRequiredVersion) }
    var latestVersion by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.latestVersion) }
    var isForceUpdate by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.isForceUpdate) }
    var updateTitle by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.updateTitle) }
    var updateChangelog by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.updateChangelog) }
    var updateUrl by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.updateUrl) }

    // ─── Maintenance Mode State ───
    var isMaintenanceActive by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.isMaintenanceMode) }
    var maintenanceTitle by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.maintenanceTitle) }
    var maintenanceMessage by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.maintenanceMessage) }
    var buttonAction by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.buttonAction) }
    var buttonText by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.buttonText) }

    // ─── Announcement & Server State ───
    var announcementActive by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.announcementActive) }
    var announcementText by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.announcementText) }
    var audioServerMode by remember(currentCloudConfig) { mutableStateOf(currentCloudConfig.audioServerMode) }

    fun saveConfig() {
        scope.launch {
            val updated = currentCloudConfig.copy(
                minRequiredVersion = minRequiredVersion,
                latestVersion = latestVersion,
                isForceUpdate = isForceUpdate,
                updateTitle = updateTitle,
                updateChangelog = updateChangelog,
                updateUrl = updateUrl,
                isMaintenanceMode = isMaintenanceActive,
                maintenanceTitle = maintenanceTitle,
                maintenanceMessage = maintenanceMessage,
                buttonAction = buttonAction,
                buttonText = buttonText,
                announcementActive = announcementActive,
                announcementText = announcementText,
                audioServerMode = audioServerMode
            )
            AeroChaseCloudSyncManager.publishCloudConfig(
                context = context,
                newConfig = updated,
                okHttpClient = HunterApplication.dependencies.okHttpClient
            )
            Toast.makeText(context, "🚀 Cloud Configuration Saved & Published Globally!", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0E))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Owner Root Command Console",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.size(48.dp))
            }

            // Version Telemetry Bar
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "Installed Client: v$installedVersion",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Min Target: v$minRequiredVersion | Latest: v$latestVersion",
                            color = HunterTextSecondary,
                            fontSize = 10.5.sp
                        )
                    }

                    val isBlocked = AeroChaseCloudSyncManager.isVersionLower(installedVersion, minRequiredVersion) && isForceUpdate
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isBlocked) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f))
                            .border(1.dp, if (isBlocked) Color(0xFFEF4444) else Color(0xFF10B981), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isBlocked) "LOWER (BLOCKED)" else "COMPLIANT (ACTIVE)",
                            color = if (isBlocked) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // 3-Tab Navigation Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabs = listOf(
                    "🚀 Version & Update",
                    "🚧 Maintenance",
                    "⚡ Server & Alert"
                )
                items(tabs.indices.toList()) { index ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedTab == index) Color.White else HunterSurface)
                            .border(1.dp, if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .clickable { selectedTab = index }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tabs[index],
                            color = if (selectedTab == index) Color.Black else Color(0xFFA1A1AA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // ────────────── VERSION GATE & FORCE UPDATE TAB ──────────────
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isForceUpdate) Color(0xFF1E1B4B) else HunterSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isForceUpdate) Color(0xFF6366F1) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isForceUpdate) "🛡️ Force Update Barrier: ACTIVE" else "⚡ Force Update Barrier: OFF",
                                            color = if (isForceUpdate) Color(0xFFA5B4FC) else Color.White,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isForceUpdate) "Users with version < $minRequiredVersion are locked out until they update." else "All versions can use the app without lockouts.",
                                            color = HunterTextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Switch(
                                        checked = isForceUpdate,
                                        onCheckedChange = { isForceUpdate = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = Color(0xFF6366F1),
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = HunterSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Minimum Required Version (e.g. 4.95.0)",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = minRequiredVersion,
                                onValueChange = { minRequiredVersion = it },
                                placeholder = { Text("e.g. 4.95.0", color = HunterTextHint) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                text = "Latest Release Version (e.g. 4.95.1)",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = latestVersion,
                                onValueChange = { latestVersion = it },
                                placeholder = { Text("e.g. 4.95.1", color = HunterTextHint) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                text = "Update Dialog Title",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = updateTitle,
                                onValueChange = { updateTitle = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                text = "Update Changelog Bullet Points",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = updateChangelog,
                                onValueChange = { updateChangelog = it },
                                minLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                text = "Update APK / Release Link",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = updateUrl,
                                onValueChange = { updateUrl = it },
                                placeholder = { Text("https://github.com/...", color = HunterTextHint) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Button(
                                onClick = { saveConfig() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save & Enforce Version Gate",
                                    color = Color.Black,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    1 -> {
                        // ────────────── MAINTENANCE TAB ──────────────
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMaintenanceActive) Color(0xFF2A1215) else HunterSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isMaintenanceActive) Color(0xFFEF4444) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(18.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isMaintenanceActive) "🚨 Maintenance Mode: ACTIVE" else "✅ App Status: ONLINE",
                                            color = if (isMaintenanceActive) Color(0xFFEF4444) else Color(0xFF10B981),
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isMaintenanceActive) "All users are locked out with your custom popup." else "Users can stream songs normally.",
                                            color = HunterTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Switch(
                                        checked = isMaintenanceActive,
                                        onCheckedChange = { isMaintenanceActive = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = Color(0xFFEF4444),
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = HunterSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Maintenance Popup Title",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maintenanceTitle,
                                onValueChange = { maintenanceTitle = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                text = "Popup Message for Users",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maintenanceMessage,
                                onValueChange = { maintenanceMessage = it },
                                minLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                text = "Button Mode",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (buttonAction == "CLOSE_APP") Color.White else HunterSurface)
                                        .clickable {
                                            buttonAction = "CLOSE_APP"
                                            if (buttonText.isBlank() || buttonText == "Download Update") buttonText = "Close App"
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "🔴 Close App",
                                        color = if (buttonAction == "CLOSE_APP") Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (buttonAction == "UPDATE_LINK") Color.White else HunterSurface)
                                        .clickable {
                                            buttonAction = "UPDATE_LINK"
                                            if (buttonText.isBlank() || buttonText == "Close App") buttonText = "Download Update"
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "📥 Download Update",
                                        color = if (buttonAction == "UPDATE_LINK") Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Button Label Text",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = buttonText,
                                onValueChange = { buttonText = it },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = HunterSurface,
                                    unfocusedContainerColor = HunterSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Button(
                                onClick = { saveConfig() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMaintenanceActive) Color(0xFFEF4444) else Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = if (isMaintenanceActive) Color.White else Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save & Publish Maintenance Mode",
                                    color = if (isMaintenanceActive) Color.White else Color.Black,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    2 -> {
                        // ────────────── SERVER & ALERT TAB ──────────────
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = HunterSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Global In-App Announcement Banner",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Switch(
                                            checked = announcementActive,
                                            onCheckedChange = { announcementActive = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = Color.White
                                            )
                                        )
                                    }

                                    if (announcementActive) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = announcementText,
                                            onValueChange = { announcementText = it },
                                            placeholder = { Text("Announcement text shown on top of home screen...", color = HunterTextHint) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color.White,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                                focusedContainerColor = Color.Black,
                                                unfocusedContainerColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Audio Server Priority Mode",
                                color = HunterTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val serverModes = listOf("AUTO_BALANCED", "SAAVN_PRIORITY", "YOUTUBE_PRIORITY")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                serverModes.forEach { mode ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (audioServerMode == mode) Color.White else HunterSurface)
                                            .clickable { audioServerMode = mode }
                                            .padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = when(mode) {
                                                "AUTO_BALANCED" -> "Auto Balance"
                                                "SAAVN_PRIORITY" -> "Saavn 320k"
                                                else -> "YouTube HQ"
                                            },
                                            color = if (audioServerMode == mode) Color.Black else Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = { saveConfig() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Server & Alert Settings",
                                    color = Color.Black,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}
