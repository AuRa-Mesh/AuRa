package com.example.aura.mesh.nodedb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aura.meshwire.MeshWireNodeSummary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Круглый аватар узла: локальный файл (в т.ч. GIF через Coil) или сгенерированный PNG по [node.nodeIdHex].
 */
@Composable
fun MeshNodeAvatar(
    node: MeshWireNodeSummary,
    modifier: Modifier = Modifier.size(52.dp),
    contentAlpha: Float = 1f,
) {
    MeshNodeAvatarByNodeId(
        nodeIdHex = node.nodeIdHex,
        contentDescription = node.displayLongName(),
        modifier = modifier,
        contentAlpha = contentAlpha,
    )
}

/**
 * То же, что [MeshNodeAvatar], по строке id (например шапка чата до загрузки [MeshWireNodeSummary]).
 */
@Composable
fun MeshNodeAvatarByNodeId(
    nodeIdHex: String,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(52.dp),
    contentAlpha: Float = 1f,
) {
    val ctx = LocalContext.current
    val rev by NodeAvatarStore.revision.collectAsStateWithLifecycle()
    val key = remember(nodeIdHex) { NodeAvatarStore.sanitizeKey(nodeIdHex) }

    val bundledRes = remember(nodeIdHex) { BundledNodeAvatars.rawResForNodeId(nodeIdHex) }
    if (bundledRes != null) {
        BundledNodeAvatarView(
            rawRes = bundledRes,
            modifier = modifier,
            contentDescription = contentDescription ?: key,
            contentAlpha = contentAlpha,
        )
        return
    }

    var displayFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(key, rev) {
        displayFile = withContext(Dispatchers.IO) {
            NodeAvatarStore.ensureAvatarDisplayFile(ctx.applicationContext, nodeIdHex)
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0D1A2E)),
        contentAlignment = Alignment.Center,
    ) {
        val f = displayFile
        if (f != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(f)
                    .crossfade(120)
                    .build(),
                contentDescription = contentDescription ?: key,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = contentAlpha,
            )
        }
    }
}

@Composable
fun MeshNodeAvatar(
    node: MeshWireNodeSummary,
    size: Dp,
    contentAlpha: Float = 1f,
) {
    MeshNodeAvatar(
        node = node,
        modifier = Modifier.size(size),
        contentAlpha = contentAlpha,
    )
}
