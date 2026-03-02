package com.gnzalobnites.appsusagemonitor.ui.stats

import android.os.Bundle

class StatisticsFragmentArgs private constructor(val packageName: String) {
    companion object {
        fun fromBundle(bundle: Bundle): StatisticsFragmentArgs {
            return StatisticsFragmentArgs(
                packageName = bundle.getString("packageName") ?: ""
            )
        }
    }
}
