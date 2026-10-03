package com.sushanth.poplauncher.search

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushanth.poplauncher.MainActivity
import com.sushanth.poplauncher.data.model.AppEntity
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.WebOverviewEntity

@Composable
fun SearchResultsList(
    results: List<SearchEntity>,
    webOverview: WebOverviewEntity?,
    hasActiveBubble: Boolean,
    query: String,
    onEntityClick: (SearchEntity) -> Unit,
    getAppIcon: (String) -> Drawable?,
    modifier: Modifier = Modifier,
    providerErrors: List<String> = emptyList()
) {
    val context = LocalContext.current

    if (results.isEmpty() && webOverview == null && query.isNotBlank() && !hasActiveBubble) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (providerErrors.isNotEmpty()) "Permission required" else "No direct match",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val description = if (providerErrors.isNotEmpty()) {
                    providerErrors.joinToString("\n")
                } else {
                    "Try searching with a different keyword or application name."
                }
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                if (providerErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            (context as? MainActivity)?.requestSearchPermissions()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant access", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        return
    }

    val uniqueResults = remember(results) { results.distinctBy { it.id } }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Permission Prompt Banner at top of search results if permissions are missing
        if (providerErrors.isNotEmpty()) {
            item(key = "permission_prompt_banner") {
                PermissionPromptCard(
                    errors = providerErrors,
                    onRequestPermissions = {
                        (context as? MainActivity)?.requestSearchPermissions()
                    }
                )
            }
        }

        // If AI Overview is available (zero local results)
        if (webOverview != null && results.isEmpty()) {
            item(key = "ai_overview") {
                SearchResultItem(
                    entity = webOverview,
                    appIcon = null,
                    getAppIcon = getAppIcon,
                    onClick = { onEntityClick(webOverview) },
                    onSubActionClick = { onEntityClick(it) }
                )
            }
        }

        items(
            items = uniqueResults,
            key = { it.id }
        ) { entity ->
            val iconPackage = when (entity) {
                is AppEntity -> entity.packageName
                is ConversationEntity -> entity.appPackage
                else -> null
            }
            val icon = iconPackage?.let { getAppIcon(it) }
            SearchResultItem(
                entity = entity,
                appIcon = icon,
                getAppIcon = getAppIcon,
                onClick = { onEntityClick(entity) },
                onSubActionClick = { onEntityClick(it) }
            )
        }
    }
}

@Composable
private fun PermissionPromptCard(
    errors: List<String>,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Search Access Required",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = errors.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Grant Search Access", fontWeight = FontWeight.SemiBold)
        }
    }
}
