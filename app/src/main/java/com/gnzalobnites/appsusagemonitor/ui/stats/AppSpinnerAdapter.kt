package com.gnzalobnites.appsusagemonitor.ui.stats

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.gnzalobnites.appsusagemonitor.databinding.ItemAppSpinnerBinding

class AppSpinnerAdapter(context: Context, private val packages: List<String>) :
    ArrayAdapter<String>(context, 0, packages) {

    private val packageManager: PackageManager = context.packageManager

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: ItemAppSpinnerBinding
        val view: View

        if (convertView == null) {
            binding = ItemAppSpinnerBinding.inflate(LayoutInflater.from(context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as ItemAppSpinnerBinding
            view = convertView
        }

        val packageName = packages[position]

        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            binding.appName.text = packageManager.getApplicationLabel(appInfo).toString()
            binding.appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
        } catch (e: PackageManager.NameNotFoundException) {
            binding.appName.text = packageName
            binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        return view
    }
}
