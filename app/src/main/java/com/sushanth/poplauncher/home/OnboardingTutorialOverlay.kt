package com.sushanth.poplauncher.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushanth.poplauncher.LauncherScreen
import com.sushanth.poplauncher.settings.isDefaultLauncher

@Composable
fun OnboardingTutorialOverlay(
    isVisible: Boolean,
    isSearchOpen: Boolean,
    currentPage: Int,
    currentScreen: LauncherScreen,
    searchFloatX: Float,
    searchFloatY: Float,
    onSetDefaultLauncher: () -> Unit,
    onComplete: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val isDefault = isDefaultLauncher(context)
    var currentStep by remember { mutableIntStateOf(0) }

    val totalSteps = 5

    // Action-gated auto-progression listeners
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen && currentStep == 0) {
            currentStep = 1
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage == 1 && currentStep <= 1) {
            currentStep = 2
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == LauncherScreen.SETTINGS && currentStep <= 2) {
            currentStep = 3
        } else if (currentScreen == LauncherScreen.FRICTION_APPS && currentStep <= 3) {
            currentStep = 4
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {

        // Step-specific Spotlight Visual Anchors that tracking element movement
        when (currentStep) {
            0 -> {
                // Spotlight around moving Search Button (Follows searchFloatX/Y)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 44.dp)
                        .offset(x = searchFloatX.dp, y = searchFloatY.dp)
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(
                            3.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
            1 -> {
                // Spotlight / Arrow pointing right for Widget page swipe
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        .border(
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwipeRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            2 -> {
                // Spotlight for Home screen Settings Long-Press
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .border(
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            3 -> {
                // Spotlight pointing to Settings Friction Apps option
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            4 -> {
                // Friction Apps Selection Window
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BubbleChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Floating Tooltip Step Card - Positioned so it NEVER obstructs the target interactive element
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .align(
                    when (currentStep) {
                        0 -> Alignment.TopCenter
                        1 -> Alignment.CenterStart
                        2 -> Alignment.BottomCenter
                        3 -> Alignment.BottomCenter
                        else -> Alignment.Center
                    }
                )
                .padding(
                    top = if (currentStep == 0) 60.dp else 20.dp,
                    bottom = if (currentStep == 2 || currentStep == 3) 32.dp else 20.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            border = BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Step Progress Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEP ${currentStep + 1} OF $totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Interactive Guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Step Content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TutorialStep"
                ) { step ->
                    when (step) {
                        0 -> StepCardContent(
                            icon = Icons.Outlined.Search,
                            title = "1. Open Universal Search",
                            subtitle = "ACTION REQUIRED: Tap the moving Search Orb",
                            description = "Tap the search orb floating at the bottom to open Universal Search and explore apps, contacts, photos, files, and web overview."
                        )
                        1 -> StepCardContent(
                            icon = Icons.Outlined.SwipeRight,
                            title = "2. Go to Widget Page",
                            subtitle = "ACTION REQUIRED: Swipe Right",
                            description = "Swipe right on your home screen to view your dedicated widget page with an 8×12 high-precision grid."
                        )
                        2 -> StepCardContent(
                            icon = Icons.Outlined.TouchApp,
                            title = "3. Open Launcher Settings",
                            subtitle = "ACTION REQUIRED: Long-press screen & open Settings",
                            description = "Long-press anywhere on the home screen to bring up the context menu, then tap Settings."
                        )
                        3 -> StepCardContent(
                            icon = Icons.Outlined.Settings,
                            title = "4. Friction Apps Window",
                            subtitle = "ACTION REQUIRED: Tap 'Friction apps'",
                            description = "Tap 'Friction apps' in Settings to open the selection window for distracting apps requiring mindful hold friction."
                        )
                        else -> Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StepCardContent(
                                icon = Icons.Outlined.Home,
                                title = "5. Default Home Launcher",
                                subtitle = "FINAL STEP: Set as Default Home App",
                                description = "Pop Launcher must be set as your Default Home App to complete setup and keep your home experience calm and fast."
                            )

                            if (!isDefault) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Home,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Default Home App Required",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = "Set Pop Launcher as default home app so pressing Home always brings you here.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        OutlinedButton(
                                            onClick = onSetDefaultLauncher,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Set Default Home App", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step Navigation Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0 && currentStep < 4) {
                        TextButton(onClick = { currentStep-- }) {
                            Text("Previous Step")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (currentStep < 4) {
                        Text(
                            text = "Perform action to advance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Button(
                            onClick = {
                                val currentlyDefault = isDefaultLauncher(context)
                                if (currentlyDefault) {
                                    onComplete()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Please set Pop Launcher as your Default Home App to complete setup.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onSetDefaultLauncher()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Get Started", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCardContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun DefaultLauncherBanner(
    isVisible: Boolean,
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 4.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Set as Default Home",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Use Pop Launcher whenever you press Home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onSetDefault,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Set", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onDismiss) {
                    Text("Later", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
