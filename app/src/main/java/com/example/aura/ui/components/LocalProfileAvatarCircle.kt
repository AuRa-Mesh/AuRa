package com.example.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun LocalProfileAvatarCircle(
    filePath: String?,
    size: Dp,
    placeholderBackground: Color,
    placeholderIconTint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String?,
) {
    val ctx = LocalContext.current
    val modelFile = remember(filePath) {
        filePath?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }?.takeIf { it.isFile }
    }
    val clipMod = Modifier.size(size).clip(CircleShape)
    val interaction = remember { MutableInteractionSource() }
    val clickMod =
        if (onClick != null || onLongClick != null) {
            Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick ?: {},
                onLongClick = onLongClick,
            )
        } else {
            Modifier
        }

    Box(
        modifier = modifier
            .then(clipMod)
            .background(placeholderBackground)
            .then(clickMod),
        contentAlignment = Alignment.Center,
    ) {
        if (modelFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(modelFile)
                    .crossfade(120)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = contentDescription,
                tint = placeholderIconTint,
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}
