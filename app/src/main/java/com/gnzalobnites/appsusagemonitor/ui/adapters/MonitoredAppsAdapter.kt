package com.gnzalobnites.appsusagemonitor.ui.adapters

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gnzalobnites.appsusagemonitor.databinding.ItemMonitoredAppBinding
import com.gnzalobnites.appsusagemonitor.data.entities.MonitoredApp
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.utils.Constants

class MonitoredAppsAdapter(
    private val onDeleteClick: (MonitoredApp) -> Unit
) : ListAdapter<MonitoredApp, MonitoredAppsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMonitoredAppBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemMonitoredAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: MonitoredApp) {
            binding.appName.text = app.appName
            binding.appInterval.text = formatInterval(app.selectedInterval)

            try {
                val pm = itemView.context.packageManager
                val icon = pm.getApplicationIcon(app.packageName)
                binding.appIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            binding.buttonRemove.setOnClickListener {
                onDeleteClick(app)
            }
        }

        private fun formatInterval(interval: Long): String {
            return when (interval) {
                Constants.INTERVAL_10_SECONDS -> itemView.context.getString(R.string.interval_every_10_seconds)
                Constants.INTERVAL_1_MINUTE -> itemView.context.getString(R.string.interval_every_1_minute)
                Constants.INTERVAL_5_MINUTES -> itemView.context.getString(R.string.interval_every_5_minutes)
                Constants.INTERVAL_15_MINUTES -> itemView.context.getString(R.string.interval_every_15_minutes)
                Constants.INTERVAL_30_MINUTES -> itemView.context.getString(R.string.interval_every_30_minutes)
                Constants.INTERVAL_1_HOUR -> itemView.context.getString(R.string.interval_every_1_hour)
                else -> itemView.context.getString(R.string.interval_custom)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MonitoredApp>() {
        override fun areItemsTheSame(oldItem: MonitoredApp, newItem: MonitoredApp): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: MonitoredApp, newItem: MonitoredApp): Boolean {
            return oldItem == newItem
        }
    }
}