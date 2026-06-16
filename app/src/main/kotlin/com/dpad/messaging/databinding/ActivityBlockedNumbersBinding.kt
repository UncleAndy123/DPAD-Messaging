// Auto-generated lightweight binding shim used by the programmatic UI rows.
// The app uses programmatic row building and only needs a container with known IDs.
package com.dpad.messaging.databinding

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

data class ActivityBlockedNumbersBinding(
    val root: View,
    val btnBack: ImageButton,
    val tvToolbarTitle: TextView,
    val keywordsContainer: LinearLayout
) {
    companion object {
        fun inflate(inflater: android.view.LayoutInflater): ActivityBlockedNumbersBinding {
            val root = inflater.inflate(com.dpad.messaging.R.layout.activity_blocked_numbers, null)
            return ActivityBlockedNumbersBinding(
                root = root,
                btnBack = root.findViewById(com.dpad.messaging.R.id.btn_back),
                tvToolbarTitle = root.findViewById(com.dpad.messaging.R.id.tv_toolbar_title),
                keywordsContainer = root.findViewById(com.dpad.messaging.R.id.keywords_container)
            )
        }
    }
}
