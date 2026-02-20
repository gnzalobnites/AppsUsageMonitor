package com.gnzalobnites.appsusagemonitor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleAppsAdapter(
    private val context: Context,
    private val apps: List<ApplicationInfo>,
    initialSelectedApps: Set<String>,
    private val onAppChecked: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SimpleAppsAdapter.ViewHolder>() {

    private val packageManager: PackageManager = context.packageManager
    private val selectedApps = initialSelectedApps.toMutableSet()
    private var isUpdating = false

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxApp)
        val appName: TextView = itemView.findViewById(R.id.textAppName)
        val appIcon: ImageView = itemView.findViewById(R.id.imageAppIcon)
        var currentPackageName: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val packageName = app.packageName

        holder.currentPackageName = packageName

        try {
            // Cargar ícono de la app
            holder.appIcon.setImageDrawable(app.loadIcon(packageManager))
            
            // Cargar nombre de la app
            holder.appName.text = app.loadLabel(packageManager).toString()

            // Configurar checkbox sin listener para evitar loops
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selectedApps.contains(packageName)

            // Listener para el checkbox
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                val currentPkg = holder.currentPackageName
                if (currentPkg == packageName && !isUpdating) {
                    isUpdating = true
                    
                    if (isChecked) {
                        selectedApps.add(packageName)
                    } else {
                        selectedApps.remove(packageName)
                    }
                    
                    onAppChecked(packageName, isChecked)
                    isUpdating = false
                }
            }

            // Click en todo el item también togglea el checkbox
            holder.itemView.setOnClickListener {
                val currentPkg = holder.currentPackageName
                if (currentPkg == packageName) {
                    holder.checkBox.isChecked = !holder.checkBox.isChecked
                }
            }

        } catch (e: Exception) {
            holder.appName.text = packageName
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateSelectedApps(newSelectedApps: Set<String>) {
        isUpdating = true
        selectedApps.clear()
        selectedApps.addAll(newSelectedApps)
        notifyDataSetChanged()
        isUpdating = false
    }

    fun getSelectedCount(): Int = selectedApps.size

    fun getSelectedApps(): Set<String> = selectedApps.toSet()
}