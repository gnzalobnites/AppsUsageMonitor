package com.gnzalobnites.appsusagemonitor.utils

object Constants {
    const val INTERVAL_10_SECONDS = 10000L
    const val INTERVAL_1_MINUTE = 60000L
    const val INTERVAL_5_MINUTES = 300000L
    const val INTERVAL_15_MINUTES = 900000L
    const val INTERVAL_30_MINUTES = 1800000L
    const val INTERVAL_1_HOUR = 3600000L
    
    const val ACTION_SHOW_BUBBLE = "com.gnzalobnites.appsusagemonitor.SHOW_BUBBLE"
    const val ACTION_HIDE_BUBBLE = "com.gnzalobnites.appsusagemonitor.HIDE_BUBBLE"
    const val ACTION_BUBBLE_CLOSED = "com.gnzalobnites.appsusagemonitor.BUBBLE_CLOSED"
    const val ACTION_UPDATE_PREVIEW = "com.gnzalobnites.appsusagemonitor.UPDATE_PREVIEW"
    
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_BADGE_COUNT = "extra_badge_count"
    const val EXTRA_INTERVAL = "extra_interval"
    const val EXTRA_SESSION_START_TIME = "extra_session_start_time"
    const val EXTRA_BUBBLE_PERSISTENT = "extra_bubble_persistent"
    const val EXTRA_PREVIEW_SIZE = "extra_preview_size"
    const val EXTRA_PREVIEW_OPACITY = "extra_preview_opacity"
    
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "screen_time_realtime"
}