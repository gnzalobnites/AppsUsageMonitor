package com.gnzalobnites.appsusagemonitor.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.databinding.FragmentMainBinding
import com.gnzalobnites.appsusagemonitor.service.MonitoringService
import com.gnzalobnites.appsusagemonitor.utils.AccessibilityHelper
import com.gnzalobnites.appsusagemonitor.utils.TimeFormatter
import com.google.android.material.R as MaterialR
import kotlinx.coroutines.launch
import java.util.*

class MainFragment : Fragment() {
    
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    
    private val dayChangeHandler = Handler(Looper.getMainLooper())
    private var lastServiceState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupButtons()
        setupFooter()
        
        viewModel.checkAccessibilityServiceState()
        viewModel.loadTodayStats()
        observeDayChange()
        
        lifecycleScope.launch {
            viewModel.isAccessibilityServiceEnabled.collect { isEnabled ->
                updateServiceButtonState(isEnabled)
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.totalScreenTime.collect { totalMs ->
                if (totalMs > 0) {
                    // Usar TimeFormatter en lugar de formatTime local
                    binding.tvTotalTime.text = TimeFormatter.formatTime(totalMs)
                } else {
                    binding.tvTotalTime.text = getString(R.string.no_usage_today)
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnSelectApps.setOnClickListener {
            findNavController().navigate(R.id.appSelectionFragment)
        }

        binding.btnServiceControl.setOnClickListener {
            val isEnabled = viewModel.isAccessibilityServiceEnabled.value
            
            if (isEnabled) {
                showAccessibilityDisableDialog()
            } else {
                if (!hasUsageStatsPermission()) {
                    Toast.makeText(
                        requireContext(), 
                        R.string.permission_usage_stats_required, 
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showAccessibilityEnableDialog()
                }
            }
        }
    }

    private fun showAccessibilityEnableDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(R.string.accessibility_permission_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                viewModel.requestAccessibilityPermission()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showAccessibilityDisableDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.stop_monitoring_title)
            .setMessage(R.string.stop_monitoring_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                viewModel.requestAccessibilityPermission()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupFooter() {
        val versionText = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            getString(R.string.version_format, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            getString(R.string.version_format_placeholder)
        }
        binding.tvVersionFooter.text = versionText

        binding.btnBuyCoffeeFooter.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/gnzbenitesh"))
            startActivity(intent)
        }
        
        binding.tvEmailFooter.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.developer_email)}")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_suggestions))
            }
            
            try {
                startActivity(emailIntent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.error_no_email_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateServiceButtonState(isEnabled: Boolean) {
        if (isEnabled && !lastServiceState) {
            Toast.makeText(requireContext(), R.string.service_running, Toast.LENGTH_SHORT).show()
        }
        
        lastServiceState = isEnabled
        
        if (isEnabled) {
            binding.btnServiceControl.text = getString(R.string.stop_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                ColorStateList.valueOf(Color.parseColor("#E57373"))
        } else {
            binding.btnServiceControl.text = getString(R.string.start_monitoring)
            binding.btnServiceControl.backgroundTintList = 
                ColorStateList.valueOf(getThemeColor(MaterialR.attr.colorPrimary))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            ) == android.app.AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            ) == android.app.AppOpsManager.MODE_ALLOWED
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun observeDayChange() {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val timeUntilMidnight = midnight.timeInMillis - System.currentTimeMillis()
        
        dayChangeHandler.postDelayed({
            viewModel.loadTodayStats()
            observeDayChange()
        }, timeUntilMidnight)
    }

    override fun onResume() {
        super.onResume()
        val enabled = AccessibilityHelper.isAccessibilityServiceEnabled(
            requireContext(),
            MonitoringService::class.java
        )
        viewModel.checkAccessibilityServiceState()
        viewModel.loadTodayStats()
        updateServiceButtonState(enabled)
    }

    override fun onPause() {
        super.onPause()
        dayChangeHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dayChangeHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
}