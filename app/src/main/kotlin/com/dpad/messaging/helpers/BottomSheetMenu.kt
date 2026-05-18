package com.dpad.messaging.helpers

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.dpad.messaging.R

/**
 * Custom styled bottom sheet menu matching app design language.
 * Uses accent colors and D-pad focusable rows.
 */
class BottomSheetMenu(context: Context) : Dialog(context) {

    data class MenuItem(
        val label: String,
        val iconRes: Int? = null,
        val action: () -> Unit
    )

    private val items = mutableListOf<MenuItem>()
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.bottom_sheet_menu)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
        }

        setCanceledOnTouchOutside(true)

        container = findViewById(R.id.menu_items_container)
        buildMenuItems()
    }

    fun addItem(label: String, iconRes: Int? = null, action: () -> Unit): BottomSheetMenu {
        items.add(MenuItem(label, iconRes, action))
        return this
    }

    private fun buildMenuItems() {
        val accentColor = ThemeManager.accentColor(context)
        val textColor = ContextCompat.getColor(context, R.color.colorOnBackground)
        val focusedTextColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(context, R.color.colorOnPrimary),
                textColor
            )
        )
        val accentTint = ColorStateList.valueOf(accentColor)

        val rowHeight = context.resources.getDimensionPixelSize(R.dimen.conversation_item_height)
        val paddingH = context.resources.getDimensionPixelSize(R.dimen.padding_medium)
        val paddingV = context.resources.getDimensionPixelSize(R.dimen.padding_small)

        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeight
                ).apply {
                    topMargin = paddingV
                }
                gravity = Gravity.CENTER_VERTICAL
                setPadding(paddingH, 0, paddingH, 0)
                isFocusable = true
                isFocusableInTouchMode = true
                background = ContextCompat.getDrawable(context, R.drawable.item_focusable_bg)
                backgroundTintList = accentTint

                setOnClickListener {
                    item.action()
                    dismiss()
                }

                setOnKeyListener { _, keyCode, event ->
                    if (isConfirmKey(keyCode) && event.action == KeyEvent.ACTION_DOWN) {
                        performClick()
                        true
                    } else false
                }
            }

            // Icon
            item.iconRes?.let { iconRes ->
                val icon = ImageView(context).apply {
                    setImageResource(iconRes)
                    imageTintList = accentTint
                    layoutParams = LinearLayout.LayoutParams(
                        context.resources.getDimensionPixelSize(R.dimen.toolbar_icon_size),
                        context.resources.getDimensionPixelSize(R.dimen.toolbar_icon_size)
                    )
                }
                row.addView(icon)

                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(paddingH, 1)
                }
                row.addView(spacer)
            }

            // Label
            val label = TextView(context).apply {
                text = item.label
                setTextColor(focusedTextColors)
                textSize = context.resources.getDimension(R.dimen.text_size_medium) / context.resources.displayMetrics.scaledDensity
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
            }
            row.addView(label)

            // Chevron
            val chevron = TextView(context).apply {
                text = "›"
                setTextColor(focusedTextColors)
                textSize = context.resources.getDimension(R.dimen.text_size_large) / context.resources.displayMetrics.scaledDensity
            }
            row.addView(chevron)

            container.addView(row)
        }
    }

    private fun isConfirmKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
    }
}
