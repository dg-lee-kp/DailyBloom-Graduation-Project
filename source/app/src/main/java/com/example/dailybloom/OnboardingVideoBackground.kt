package com.example.dailybloom

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun OnboardingVideoBackground(
    modifier: Modifier = Modifier,
    opacity: Float = 1f
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LoopingVideoBackgroundView(context).apply {
                setVideoOpacity(opacity)
                setVideoUri(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.onboarding_background}")
                )
            }
        },
        update = {
            it.setVideoOpacity(opacity)
            it.play()
        }
    )
}

private class LoopingVideoBackgroundView(context: Context) :
    FrameLayout(context),
    TextureView.SurfaceTextureListener {

    private val textureView = TextureView(context)
    private var videoUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        textureView.isOpaque = false
        addView(
            textureView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        textureView.surfaceTextureListener = this
    }

    fun setVideoOpacity(opacity: Float) {
        textureView.alpha = opacity.coerceIn(0f, 1f)
    }

    fun setVideoUri(uri: Uri) {
        videoUri = uri
        if (textureView.isAvailable) {
            preparePlayer(Surface(textureView.surfaceTexture))
        }
    }

    fun play() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                runCatching { player.start() }
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                runCatching { player.pause() }
            }
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        preparePlayer(Surface(surfaceTexture))
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        mediaPlayer?.let { updateVideoTransform(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            play()
        } else {
            pause()
        }
    }

    private fun preparePlayer(newSurface: Surface) {
        val uri = videoUri ?: return
        releasePlayer()
        surface = newSurface
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { player ->
                player.isLooping = true
                player.setVolume(0f, 0f)
                updateVideoTransform(player.videoWidth, player.videoHeight)
                player.start()
            }
            setOnVideoSizeChangedListener { _, width, height ->
                updateVideoTransform(width, height)
            }
            setOnErrorListener { _, _, _ -> true }
            runCatching {
                setDataSource(context, uri)
                setSurface(newSurface)
                prepareAsync()
            }.onFailure {
                releasePlayer()
            }
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        surface?.release()
        surface = null
    }

    private fun updateVideoTransform(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val scale = minOf(viewWidth / videoWidth.toFloat(), viewHeight / videoHeight.toFloat())
        val scaledWidth = videoWidth * scale
        val scaledHeight = videoHeight * scale

        textureView.layoutParams = LayoutParams(
            scaledWidth.toInt().coerceAtLeast(1),
            scaledHeight.toInt().coerceAtLeast(1),
            Gravity.CENTER
        )
        textureView.setTransform(Matrix())
    }
}
