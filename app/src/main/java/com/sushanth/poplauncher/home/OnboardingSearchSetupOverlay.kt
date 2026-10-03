package com.sushanth.poplauncher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sushanth.poplauncher.MainActivity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.SettingsRepository

@Composable
fun OnboardingSearchSetupOverlay(
    settingsRepository: SettingsRepository,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    // Installed Apps, Files & Documents, and Photos & Videos are enabled by default
    var selectedProviders by remember {
        mutableStateOf(setOf(SearchType.APP, SearchType.FILE, SearchType.MEDIA))
    }

    fun toggleProvider(type: SearchType, enabled: Boolean) {
        if (type == SearchType.APP) return // Installed Apps cannot be disabled
        val current = selectedProviders.toMutableSet()
        if (enabled) current.add(type) else current.remove(type)
        selectedProviders = current
    }

    BackHandler {
        // Prevent dismissal until setup is completed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            border = BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Search Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose search sources & permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Select what you want to search. All optional sources are turned OFF by default — permission will be requested ONLY for the features you turn ON.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 1. Installed Apps (ON by default)
                OnboardingProviderRow(
                    title = "Installed Apps",
                    permissionExplanation = "Required for launcher. No runtime permission dialog required.",
                    checked = selectedProviders.contains(SearchType.APP),
                    enabled = false, // Always ON
                    onCheckedChange = {}
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. People & Contacts
                OnboardingProviderRow(
                    title = "People & Contacts",
                    permissionExplanation = "Requires READ_CONTACTS permission to search contact names & phone numbers locally on device.",
                    checked = selectedProviders.contains(SearchType.PERSON),
                    onCheckedChange = { toggleProvider(SearchType.PERSON, it) }
                )

                // 3. Conversation Shortcuts
                OnboardingProviderRow(
                    title = "Conversation Shortcuts",
                    permissionExplanation = "Searches launcher shortcuts (WhatsApp, Telegram, etc.). No special permission required.",
                    checked = selectedProviders.contains(SearchType.CONVERSATION),
                    onCheckedChange = { toggleProvider(SearchType.CONVERSATION, it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 4. Files & Documents
                OnboardingProviderRow(
                    title = "Files & Documents",
                    permissionExplanation = "Searches documents and media files locally.",
                    checked = selectedProviders.contains(SearchType.FILE),
                    onCheckedChange = { toggleProvider(SearchType.FILE, it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 7. Photos & Videos
                OnboardingProviderRow(
                    title = "Photos & Videos",
                    permissionExplanation = "Requires Media / Photos permission to search photos and videos locally.",
                    checked = selectedProviders.contains(SearchType.MEDIA),
                    onCheckedChange = { toggleProvider(SearchType.MEDIA, it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 8. AI Overview
                OnboardingProviderRow(
                    title = "AI Overview",
                    permissionExplanation = "Generates AI summaries when local search has no matches.",
                    checked = selectedProviders.contains(SearchType.GOOGLE_OVERVIEW),
                    onCheckedChange = { toggleProvider(SearchType.GOOGLE_OVERVIEW, it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        // 1. Save selected search preferences
                        settingsRepository.setEnabledProviders(selectedProviders)
                        // 2. Request permissions ONLY for selected options
                        mainActivity?.requestPermissionsForProviders(selectedProviders)
                        // 3. Mark onboarding search setup completed
                        settingsRepository.setCompletedOnboarding(true)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Save & Request Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProviderRow(
    title: String,
    permissionExplanation: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = permissionExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = if (enabled) onCheckedChange else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
