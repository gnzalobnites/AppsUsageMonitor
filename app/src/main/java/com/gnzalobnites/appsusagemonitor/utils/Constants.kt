package com.gnzalobnites.appsusagemonitor.utils

object Constants {
    // Acciones
    const val ACTION_SHOW_BUBBLE = "com.gnzalobnites.appsusagemonitor.SHOW_BUBBLE"
    const val ACTION_HIDE_BUBBLE = "com.gnzalobnites.appsusagemonitor.HIDE_BUBBLE"
    const val ACTION_BUBBLE_CLOSED = "com.gnzalobnites.appsusagemonitor.BUBBLE_CLOSED"
    const val ACTION_UPDATE_SESSION_TIME = "com.gnzalobnites.appsusagemonitor.UPDATE_SESSION_TIME"
    const val ACTION_UPDATE_PROGRESS = "com.gnzalobnites.appsusagemonitor.UPDATE_PROGRESS"
    const val ACTION_BUBBLE_GOAL_REACHED = "com.gnzalobnites.appsusagemonitor.GOAL_REACHED"
    
    // Acciones para tiempo extra
    const val ACTION_EXTRA_TIME_ADDED = "com.gnzalobnites.appsusagemonitor.EXTRA_TIME_ADDED"
    const val ACTION_EXTRA_TIME_COMPLETED = "com.gnzalobnites.appsusagemonitor.EXTRA_TIME_COMPLETED"
    const val ACTION_EXTRA_TIME_STOPPED = "com.gnzalobnites.appsusagemonitor.EXTRA_TIME_STOPPED"
    const val ACTION_CLOSE_MONITORED_APP = "com.gnzalobnites.appsusagemonitor.CLOSE_MONITORED_APP"
    const val ACTION_RESUME_MONITORING = "com.gnzalobnites.appsusagemonitor.RESUME_MONITORING"
    
    // Extras
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_BADGE_COUNT = "extra_badge_count"
    const val EXTRA_INTERVAL = "extra_interval"
    const val EXTRA_SESSION_START_TIME = "extra_session_start_time"
    const val EXTRA_BUBBLE_PERSISTENT = "extra_bubble_persistent"
    const val EXTRA_TIME_GOAL_MINUTES = "extra_time_goal_minutes"
    const val EXTRA_DURATION = "extra_duration"
    
    // Extras para tiempo extra
    const val EXTRA_EXTRA_BLOCKS = "extra_blocks"
    const val EXTRA_EXTRA_MINUTES = "extra_minutes"
    const val EXTRA_EXTRA_REMAINING = "extra_remaining"
    
    // Notificaciones
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "screen_time_realtime"
    const val GOAL_NOTIFICATION_ID = 1002
    const val GOAL_NOTIFICATION_CHANNEL_ID = "goal_reached"
}