package com.dpad.messaging.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dpad.messaging.R

/**
 * Per-contact message colors.
 *
 * The user can pick a color for a phone number; that color is used for the
 * conversation avatar and for that contact's received message bubbles.
 * Colors are keyed by the normalized (digits-only) phone number.
 */
object ContactColors {

    /** Curated palette (Material 600 tones) that works on light and dark themes. */
    val PALETTE = intArrayOf(
        0xFFE53935.toInt(), // Red
        0xFFD81B60.toInt(), // Pink
        0xFF8E24AA.toInt(), // Purple
        0xFF5E35B1.toInt(), // Deep Purple
        0xFF3949AB.toInt(), // Indigo
        0xFF1E88E5.toInt(), // Blue
        0xFF0097A7.toInt(), // Cyan
        0xFF00897B.toInt(), // Teal
        0xFF43A047.toInt(), // Green
        0xFF7CB342.toInt(), // Light Green
        0xFFF4511E.toInt(), // Deep Orange
        0xFF6D4C41.toInt()  // Brown
    )

    private val PALETTE_NAMES = arrayOf(
        "Red", "Pink", "Purple", "Deep purple", "Indigo", "Blue",
        "Cyan", "Teal", "Green", "Light green", "Orange", "Brown"
    )

    /** Digits-only form used as the storage key. */
    fun normalize(phoneNumber: String): String =
        phoneNumber.filter { it.isDigit() }.ifBlank { phoneNumber.trim() }

    /** User-assigned color for [phoneNumber], or null if unset. */
    fun customColor(phoneNumber: String): Int? =
        Prefs.get().getContactColor(normalize(phoneNumber))

    fun colorName(color: Int?): String {
        if (color == null) return "Automatic"
        val index = PALETTE.indexOf(color)
        return PALETTE_NAMES.getOrElse(index) { "Custom" }
    }

    /**
     * Best color for [phoneNumber] = custom color if set,
     * otherwise a stable hash-derived color (so untouched contacts still look nice).
     */
    fun resolveColor(phoneNumber: String): Int =
        customColor(phoneNumber) ?: defaultFor(phoneNumber)

    /** Stable, readable HSL colour derived from a seed string (number or name). */
    fun defaultFor(seed: String): Int {
        val hue = (Math.abs(seed.hashCode()) % 360).toFloat()
        return ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.35f))
    }

    /** High-contrast text colour to place on top of [background]. */
    fun textColorOn(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) < 0.5) Color.WHITE else 0xFF1B1F24.toInt()

    /** Slightly transparent meta colour to place on top of [background]. */
    fun metaColorOn(background: Int): Int =
        if (ColorUtils.calculateLuminance(background) < 0.5) 0xCCFFFFFF.toInt() else 0xAA1B1F24.toInt()

    // ── Drawables ───────────────────────────────────────────────────────────

    private fun roundedRect(
        color: Int,
        radiusDp: Float,
        strokeWidth: Int,
        strokeColor: Int = 0xFF00FFFF.toInt()
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    /** Rounded-square swatch with separate selected and D-pad-focused states. */
    fun swatchDrawable(color: Int, selected: Boolean = false): Drawable {
        val normal = roundedRect(color, 8f, 0)
        val focused = roundedRect(color, 8f, 7)
        val selectedState = roundedRect(color, 8f, 3, textColorOn(color))
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            if (selected) addState(intArrayOf(android.R.attr.state_selected), selectedState)
            addState(IntArray(0), normal)
        }
    }

    /**
     * Received-bubble background using [color]. Overall shape matches
     * drawable/bubble_received.xml; focus state adds the cyan ring.
     */
    fun receivedBubbleDrawable(context: Context, color: Int): Drawable {
        val normal = roundedRect(color, 0f, 0).apply {
            cornerRadii = floatArrayOf(10f, 10f, 10f, 10f, 10f, 10f, 2f, 2f)
        }
        val focused = roundedRect(
            ContextCompat.getColor(context, R.color.focus_fill),
            0f,
            1,
            ContextCompat.getColor(context, R.color.focus_border)
        ).apply {
            cornerRadii = floatArrayOf(10f, 10f, 10f, 10f, 10f, 10f, 2f, 2f)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(IntArray(0), normal)
        }
    }

    // ── Picker dialog ───────────────────────────────────────────────────────

    /**
     * Shows a D-Pad-friendly color picker.
     * [currentColor] selects the matching swatch when the dialog opens;
     * [onSelected] receives the saved color, or null to clear back to default.
     */
    fun showColorPicker(
        context: Context,
        title: String,
        currentColor: Int?,
        onSelected: (Int?) -> Unit,
        onDismiss: (() -> Unit)? = null
    ) {
        val density = context.resources.displayMetrics.density
        val spacingPx = (density * 8f).toInt()
        val paddingPx = (density * 16f).toInt()

        val colors = PALETTE.toList() + null  // null represents "default"
        val widthDp = context.resources.displayMetrics.widthPixels / density
        val columns = if (widthDp < 280f) 3 else 4

        val pickerAdapter = ColorPickerAdapter(colors, currentColor)
        val recycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, columns)
            adapter = pickerAdapter
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            addItemDecoration(SwatchItemDecoration(spacingPx))
            // Limit max height to fit on small screens (Sonim XP3900: 320dp height)
            val maxHeight = (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.height = maxHeight
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(recycler)
            .setPositiveButton(R.string.save) { _, _ -> onSelected(pickerAdapter.selectedColor) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val target = currentColor?.let { PALETTE.indexOf(it) } ?: colors.size - 1
            recycler.layoutManager?.scrollToPosition(target)
            recycler.post {
                recycler.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
            }
            // Ensure dialog fits on screen
            dialog.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                // Limit height to 70% of screen height
                val maxHeight = (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
                if (attributes.height > maxHeight) {
                    attributes.height = maxHeight
                }
            }
        }
        dialog.setOnDismissListener { onDismiss?.invoke() }

        dialog.show()
    }

    private class ColorPickerAdapter(
        private val colors: List<Int?>,
        currentColor: Int?
    ) : RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>() {

        var selectedColor: Int? = currentColor
            private set

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val swatchSizePx = (density * 48f).toInt()
            val check = TextView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = "✓"
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val view = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(swatchSizePx, swatchSizePx)
                isFocusable = true
                isFocusableInTouchMode = true
                addView(check)
            }
            return ViewHolder(view, check)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colors[position]
            val displayColor = color ?: 0xFF607D8B.toInt()
            val selected = color == selectedColor
            holder.itemView.apply {
                isSelected = selected
                background = swatchDrawable(displayColor, selected)
                contentDescription = context.getString(
                    if (selected) R.string.contact_color_option_selected
                    else R.string.contact_color_option,
                    ContactColors.colorName(color)
                )
                holder.check.apply {
                    visibility = if (selected) View.VISIBLE else View.GONE
                    setTextColor(textColorOn(displayColor))
                }
                setOnClickListener {
                    selectedColor = color
                    notifyDataSetChanged()
                }
                if (color == selectedColor) {
                    requestFocus()
                }
            }
        }

        override fun getItemCount() = colors.size

        class ViewHolder(view: FrameLayout, val check: TextView) : RecyclerView.ViewHolder(view)
    }

    private class SwatchItemDecoration(private val spacingPx: Int) :
        RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.set(spacingPx, spacingPx, spacingPx, spacingPx)
        }
    }
}
