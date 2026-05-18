package com.dpad.messaging.activities

import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.util.Log
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.RequestListener
import com.dpad.messaging.R
import kotlin.math.max
import com.dpad.messaging.BuildConfig
import kotlin.math.roundToInt

class ImageViewerActivity : BaseActivity() {
    private lateinit var imageView: ImageView
    private lateinit var zoomLabel: TextView

    private val matrixValues = Matrix()
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val zoomLevels = floatArrayOf(1f, 2f, 3f, 4f)
    private val panStepPx = 96f
    private val keyTag = "ImageViewerKeys"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        imageView = findViewById(R.id.iv_full_image)
        zoomLabel = findViewById(R.id.tv_zoom_level)
        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)

        if (uriString.isNullOrBlank()) {
            finish()
            return
        }

        Glide.with(this)
            .load(Uri.parse(uriString))
            .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    imageView.post { resetTransform() }
                    return false
                }
            })
            .into(imageView)

        imageView.setOnClickListener { finish() }
        imageView.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (BuildConfig.DEBUG && event != null) {
            Log.d(
                keyTag,
                "keyCode=$keyCode action=${event.action} source=${event.source} device=${event.deviceId} repeat=${event.repeatCount}"
            )
        }
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_STAR -> {
                resetTransform()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_VOLUME_UP -> {
                zoomIn()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                zoomOut()
                return true
            }
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_NUMPAD_4 -> {
                panBy(panStepPx, 0f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                panBy(panStepPx, 0f)
                return true
            }
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_NUMPAD_6 -> {
                panBy(-panStepPx, 0f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                panBy(-panStepPx, 0f)
                return true
            }
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_NUMPAD_2 -> {
                panBy(0f, panStepPx)
                return true
            }
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_NUMPAD_8 -> {
                panBy(0f, -panStepPx)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun zoomIn() {
        val currentIndex = zoomLevels.indexOfFirst { it >= scaleFactor - 0.01f }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(zoomLevels.lastIndex)
        scaleFactor = zoomLevels[nextIndex]
        applyTransform()
    }

    private fun zoomOut() {
        val currentIndex = zoomLevels.indexOfLast { it <= scaleFactor + 0.01f }
        val prevIndex = if (currentIndex <= 0) 0 else currentIndex - 1
        scaleFactor = zoomLevels[prevIndex]
        if (scaleFactor <= 1f) {
            offsetX = 0f
            offsetY = 0f
        }
        applyTransform()
    }

    private fun panBy(dx: Float, dy: Float) {
        if (scaleFactor <= 1f) return
        offsetX += dx
        offsetY += dy
        applyTransform()
    }

    private fun resetTransform() {
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        applyTransform()
    }

    private fun applyTransform() {
        val drawable = imageView.drawable ?: return
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        val drawW = drawable.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val drawH = drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
        if (viewW <= 0f || viewH <= 0f) return

        val baseScale = minOf(viewW / drawW, viewH / drawH)
        val actualScale = baseScale * scaleFactor

        val scaledW = drawW * actualScale
        val scaledH = drawH * actualScale

        val maxOffsetX = max(0f, (scaledW - viewW) / 2f)
        val maxOffsetY = max(0f, (scaledH - viewH) / 2f)

        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

        val translateX = (viewW - scaledW) / 2f + offsetX
        val translateY = (viewH - scaledH) / 2f + offsetY

        matrixValues.reset()
        matrixValues.postScale(actualScale, actualScale)
        matrixValues.postTranslate(translateX, translateY)
        imageView.imageMatrix = matrixValues

        zoomLabel.text = getString(R.string.image_viewer_zoom_percent, (scaleFactor * 100).roundToInt())
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}
