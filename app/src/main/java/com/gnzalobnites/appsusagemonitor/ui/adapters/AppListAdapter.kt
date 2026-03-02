package com.gnzalobnites.appsusagemonitor.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.ui.selection.AppInfo

class AppListAdapter(
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var apps = listOf<AppInfo>()

    fun submitList(list: List<AppInfo>) {
        apps = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val appCheckbox: CheckBox = itemView.findViewById(R.id.app_checkbox)

        fun bind(app: AppInfo) {
            appName.text = app.appName
            appIcon.setImageDrawable(app.icon)
            
            appCheckbox.setOnCheckedChangeListener(null)
            appCheckbox.isChecked = app.isSelected
            
            appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != app.isSelected) {
                    itemView.post {
                        onItemClick(app)
                    }
                }
            }

            itemView.setOnClickListener {
                appCheckbox.isChecked = !appCheckbox.isChecked
            }
        }
    }
}
