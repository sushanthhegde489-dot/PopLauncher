package com.sushanth.poplauncher.search

import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.MaterialTheme
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.repository.SearchResultState
import kotlinx.coroutines.delay
import kotlin.time.Duration

@Composable
fun SearchOverlay(
    isVisible: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchState: SearchResultState,
    onDismiss: () -> Unit,
    onLaunchEntity: (SearchEntity) -> Unit,
    getAppIcon: (String) -> Drawable?,
    modifier: Modifier = Modifier,
    onSubmitSearch: ((String) -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = isVisible) {
        onDismiss()
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            for (attempt in 0 until 4) {
                delay((if (attempt == 0) 100L else 75L).milliseconds)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    break
                } catch (_: Exception) {}
            }
        } else {
            try {
                focusManager.clearFocus()
                keyboardController?.hide()
            } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDismiss()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consume clicks inside search content area
                    }
            ) {
                // Minimal Search Field
                SearchField(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    onSearchAction = {
                        if (onSubmitSearch != null) {
                            onSubmitSearch(searchQuery)
                        } else {
                            val first = searchState.standardResults.firstOrNull() ?: searchState.webOverview
                            if (first != null) {
                                onLaunchEntity(first)
                                onDismiss()
                            }
                        }
                    },
                    placeholderText = "Type app, contact, or file...",
                    focusRequester = focusRequester
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Search Results or Google Overview
                SearchResultsList(
                    results = searchState.standardResults,
                    webOverview = searchState.webOverview,
                    providerErrors = searchState.providerErrors,
                    hasActiveBubble = searchState.activeBubbleApp != null,
                    query = searchQuery,
                    onEntityClick = { entity ->
                        onLaunchEntity(entity)
                        onDismiss()
                    },
                    getAppIcon = getAppIcon,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
