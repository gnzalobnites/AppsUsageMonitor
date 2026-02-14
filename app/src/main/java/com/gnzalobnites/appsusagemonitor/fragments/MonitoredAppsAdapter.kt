package com.gnzalobnites.appsusagemonitor

import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MonitoredAppsAdapter(
    private val apps: List<ApplicationInfo>,
    private val packageManager: PackageManager,
    private val onAppChecked: (String, Boolean) -> Unit,
    initialSelectedApps: Set<String> = emptySet()
) : RecyclerView.Adapter<MonitoredAppsAdapter.AppViewHolder>() {

    private val selectedApps = mutableSetOf<String>()
    // ✅ ELIMINADA: private var isDarkMode = false
    // ✅ ELIMINADO: setThemeColors()
    // ✅ ELIMINADO: applyThemeColors()

    init {
        selectedApps.addAll(initialSelectedApps)
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val appPackage: TextView = itemView.findViewById(R.id.appPackage)
        val checkBox: CheckBox = itemView.findViewById(R.id.appCheckBox)
        
        init {
            // ✅ Configuración adicional si es necesaria
            // El tema ya se aplica automáticamente por el XML
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        val packageName = app.packageName
        
        try {
            holder.appIcon.setImageDrawable(app.loadIcon(packageManager))
            holder.appName.text = app.loadLabel(packageManager).toString()
            holder.appPackage.text = packageName
            holder.checkBox.isChecked = selectedApps.contains(packageName)
            
            // ✅ NO LLAMAR applyThemeColors() - Android lo maneja automáticamente
            
            // Remover listener anterior para evitar duplicados
            holder.checkBox.setOnCheckedChangeListener(null)
            
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onAppChecked(packageName, isChecked)
                if (isChecked) {
                    selectedApps.add(packageName)
                } else {
                    selectedApps.remove(packageName)
                }
            }
            
            holder.itemView.setOnClickListener {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
            }
            
        } catch (e: Exception) {
            holder.appName.text = "Error"
            holder.appPackage.text = packageName
        }
    }

    override fun getItemCount(): Int = apps.size
    
    fun updateSelectedApps(newSelectedApps: Set<String>) {
        selectedApps.clear()
        selectedApps.addAll(newSelectedApps)
        notifyDataSetChanged()
    }
    
    fun getSelectedApps(): Set<String> = selectedApps.toSet()
}