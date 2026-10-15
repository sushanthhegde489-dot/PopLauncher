package com.sushanth.poplauncher.search

import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.sushanth.poplauncher.data.model.AnimationLevel
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.repository.SearchResultState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SearchOverlay(
    isVisible: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchState: SearchResultState,
    onDismiss: () -> Unit,
    onLaunchEntity: (SearchEntity) -> Unit,
    getAppIcon: (String, UserHandle?) -> Drawable?,
    modifier: Modifier = Modifier,
    animationLevel: AnimationLevel = AnimationLevel.STANDARD,
    onSubmitSearch: ((String) -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var selectedCategory by remember(searchQuery) { mutableStateOf(SearchResultCategory.ALL) }

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

    val overlayEnter = if (animationLevel == AnimationLevel.OFF) EnterTransition.None else {
        val duration = (260 * animationLevel.scale).toInt().coerceAtLeast(80)
        fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 3 }
    }
    val overlayExit = if (animationLevel == AnimationLevel.OFF) ExitTransition.None else {
        val duration = (200 * animationLevel.scale).toInt().coerceAtLeast(80)
        fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { it / 3 }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = overlayEnter,
        exit = overlayExit,
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
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consume clicks inside search content area
                    }
            ) {
                // 1. Search Results (Above categories & search bar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SearchResultsList(
                        results = searchState.standardResults,
                        webOverview = searchState.webOverview,
                        providerErrors = searchState.providerErrors,
                        hasActiveBubble = searchState.activeBubbleApp != null,
                        query = searchQuery,
                        selectedCategory = selectedCategory,
                        onEntityClick = { entity ->
                            // Note: onLaunchEntity handles dismissal or transitioning to activeBubbleApp without wiping bubble state
                            onLaunchEntity(entity)
                        },
                        getAppIcon = getAppIcon,
                        animationLevel = animationLevel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Search Categories Bar (Just above the search bar)
                SearchCategoriesBar(
                    results = searchState.standardResults,
                    webOverview = searchState.webOverview,
                    query = searchQuery,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Minimal Search Field (Bottom, just above keyboard)
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
                            }
                        }
                    },
                    placeholderText = "Type app, contact, or file...",
                    focusRequester = focusRequester
                )
            }
        }
    }
}
