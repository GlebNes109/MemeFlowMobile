package com.memeflow.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.memeflow.app.core.common.formatTimestamp
import com.memeflow.app.core.common.uiLabel
import com.memeflow.app.core.model.AccessLevel
import com.memeflow.app.core.model.MediaKind
import com.memeflow.app.core.model.MediaStatus
import com.memeflow.app.core.model.Meme
import com.memeflow.app.core.model.MemeCollection
import com.memeflow.app.core.model.ModerationStatus
import com.memeflow.app.core.model.UserSummary

@Composable
fun MemeFlowBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        content()
    }
}

@Composable
fun Avatar(user: UserSummary, modifier: Modifier = Modifier) {
    AsyncImage(
        model = user.avatarUrl,
        contentDescription = user.displayName,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun AccessBadge(accessLevel: AccessLevel) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when (accessLevel) {
            AccessLevel.PRIVATE -> MaterialTheme.colorScheme.surfaceVariant
            AccessLevel.GROUPS -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
            AccessLevel.PUBLIC -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        }
    ) {
        Text(
            text = accessLevel.uiLabel(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = when (accessLevel) {
                AccessLevel.PRIVATE -> MaterialTheme.colorScheme.onSurfaceVariant
                AccessLevel.GROUPS -> MaterialTheme.colorScheme.secondary
                AccessLevel.PUBLIC -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
fun ModerationBadge(status: ModerationStatus) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when (status) {
            ModerationStatus.PENDING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
            ModerationStatus.APPROVED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
            ModerationStatus.REJECTED -> Color(0xFFB4452F).copy(alpha = 0.14f)
        }
    ) {
        Text(
            text = when (status) {
                ModerationStatus.PENDING -> "pending"
                ModerationStatus.APPROVED -> "approved"
                ModerationStatus.REJECTED -> "rejected"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun TagRow(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = {},
                label = { Text("#$tag") }
            )
        }
    }
}

@Composable
fun MemeCard(
    meme: Meme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(user = meme.author)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = meme.author.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        text = "@${meme.author.login} · ${formatTimestamp(meme.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AccessBadge(meme.effectiveVisibility)
            }

            MemeMedia(mediaUrl = meme.media.thumbnailUrl ?: meme.media.storageUrl ?: meme.media.originalUrl, kind = meme.media.kind)

            Text(text = meme.caption, style = MaterialTheme.typography.bodyLarge)
            TagRow(tags = meme.tags)

            footer?.invoke()
        }
    }
}

@Composable
fun CollectionCard(
    collection: MemeCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    AsyncImage(
                        model = collection.coverThumbnailUrl,
                        contentDescription = collection.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = collection.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${collection.itemCount} items · @${collection.author.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AccessBadge(collection.visibility)
            }
            if (collection.description.isNotBlank()) {
                Text(
                    text = collection.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemeMedia(
    mediaUrl: String?,
    kind: MediaKind,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = mediaUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop
        )
        if (kind == MediaKind.EXTERNAL_VIDEO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.PlayCircleOutline,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(text = "Short", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FullScreenError(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            modifier = Modifier
                .padding(24.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusBadge(status: MediaStatus) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    ) {
        Text(
            text = status.name.lowercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AccessLegend(accessLevels: List<AccessLevel>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accessLevels.forEach { AccessBadge(it) }
    }
}
