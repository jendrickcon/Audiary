package com.example.audiary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.audiary.model.Song
import com.example.audiary.model.MusicSource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import kotlin.math.absoluteValue

/** Original procedural artwork for the demo catalog, never a substitute for a claimed album cover. */
@Composable fun Cover(song: Song, modifier: Modifier = Modifier) {
    if (song.source == MusicSource.Demo && song.artUrl == null) {
        DemoArtwork(song, modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(song.artUrl, context) {
        ImageRequest.Builder(context).data(song.artUrl).diskCachePolicy(CachePolicy.DISABLED).build()
    }
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Album, "Artwork unavailable", Modifier.fillMaxSize(.4f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (song.artUrl != null) AsyncImage(model = request, contentDescription = "Album artwork for ${song.album}",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable private fun DemoArtwork(song: Song, modifier: Modifier = Modifier) {
    val base = Color(song.artColor)
    val variant = remember(song.albumId, song.id) { (song.albumId.ifBlank { song.id }.hashCode().toLong().absoluteValue % 6).toInt() }
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(base)) {
        val w = size.width
        val h = size.height
        val light = Color(0xFFF4E9CB)
        val dark = lerp(base, Color.Black, 0.7f)
        drawRect(Brush.linearGradient(listOf(lerp(base, light, .25f), base, dark)))
        when (variant) {
            0 -> {
                drawCircle(light.copy(alpha = .9f), w * .22f, Offset(w * .63f, h * .35f))
                repeat(8) { i -> drawRect(dark.copy(alpha = .65f), Offset(0f, h * (.56f + i * .055f)), Size(w, h * .022f)) }
                drawRect(base.copy(alpha = .6f), Offset(w * .15f, 0f), Size(w * .14f, h))
            }
            1 -> repeat(10) { i ->
                drawCircle(if (i % 2 == 0) light.copy(alpha = .55f) else dark, w * (.85f - i * .08f), Offset(w * .73f, h * .56f))
            }
            2 -> rotate(-28f) {
                repeat(9) { i ->
                    drawRect(if (i % 3 == 0) light.copy(alpha = .8f) else dark.copy(alpha = .7f),
                        Offset(w * (-.4f + i * .22f), -h * .4f), Size(w * .1f, h * 1.8f))
                }
            }
            3 -> {
                repeat(6) { i -> drawRoundRect(light.copy(alpha = .12f + i * .05f),
                    Offset(w * (.08f + i * .06f), h * (.07f + i * .065f)),
                    Size(w * (.82f - i * .11f), h * (.87f - i * .1f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .4f, w * .4f),
                    style = Stroke(w * .025f)) }
                drawCircle(light, w * .08f, Offset(w * .5f, h * .52f))
            }
            4 -> {
                drawCircle(dark, w * .55f, Offset(w * .24f, h * .9f))
                drawCircle(light.copy(alpha = .8f), w * .29f, Offset(w * .71f, h * .25f))
                repeat(16) { i -> drawLine(base.copy(alpha = .9f), Offset(0f, h * i / 16), Offset(w, h * i / 16), w * .014f) }
            }
            else -> {
                repeat(5) { i ->
                    drawRect(if (i % 2 == 0) light.copy(alpha = .65f) else dark,
                        Offset(w * i / 5, h * (.15f + .08f * i)), Size(w * .15f, h))
                }
                drawCircle(base, w * .24f, Offset(w * .5f, h * .63f))
                drawCircle(light, w * .05f, Offset(w * .5f, h * .63f))
            }
        }
    }
}
@Composable fun Centered(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}
