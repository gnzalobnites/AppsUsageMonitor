package com.gnzalobnites.appsusagemonitor.ui.adapters

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp

class MonitoredAppsSimpleAdapter(
    private val onDeleteClick: (MonitoredApp) -> Unit
) : RecyclerView.Adapter<MonitoredAppsSimpleAdapter.ViewHolder>() {

    private var apps = listOf<MonitoredApp>()

    fun submitList(list: List<MonitoredApp>) {
        apps = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monitored_app_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val appInterval: TextView = itemView.findViewById(R.id.app_interval)
        private val btnRemove: android.widget.ImageButton = itemView.findViewById(R.id.button_remove)

        fun bind(app: MonitoredApp) {
            appName.text = app.appName
            appInterval.text = formatInterval(app.selectedInterval)

            try {
                val pm = itemView.context.packageManager
                val icon = pm.getApplicationIcon(app.packageName)
                appIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            btnRemove.setOnClickListener {
                onDeleteClick(app)
            }
        }

        private fun formatInterval(interval: Long): String {
            return when (interval) {
                com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_10_SECONDS -> 
                    itemView.context.getString(R.string.interval_every_10_seconds)
                com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_1_MINUTE -> 
                    itemView.context.getString(R.string.interval_every_1_minute)
                com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_5_MINUTES -> 
                    itemView.context.getString(R.string.interval_every_5_minutes)
                com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_15_MINUTES -> 
                    itemView.context.getString(R.string.interval_every_15_minutes)
                com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_30_MINUTES -> 
                    itemView.context.getString(R.string.interval_every_30_minutes)
                com.gnzalobnites.appsusagemonitor.utils.Constants.INTERVAL_1_HOUR -> 
                    itemView.context.getString(R.string.interval_every_1_hour)
                else -> itemView.context.getString(R.string.interval_custom)
            }
        }
    }
}