package com.gnzalobnites.appsusagemonitor.ui.selection

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.databinding.FragmentAppSelectionBinding
import com.gnzalobnites.appsusagemonitor.ui.adapters.AppListAdapter
import com.gnzalobnites.appsusagemonitor.ui.adapters.MonitoredAppsSimpleAdapter
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppSelectionFragment : Fragment() {
    
    private var _binding: FragmentAppSelectionBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AppSelectionViewModel by viewModels()
    private lateinit var appsAdapter: AppListAdapter
    private lateinit var monitoredAdapter: MonitoredAppsSimpleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSpinner()
        setupRecyclerViews()
        setupSearch()
        setupObservers()
        setupListeners()
        
        viewModel.loadInstalledApps()
    }

    private fun setupSpinner() {
        val intervals = listOf(
            getString(R.string.interval_10_seconds),
            getString(R.string.interval_1_minute),
            getString(R.string.interval_5_minutes),
            getString(R.string.interval_15_minutes),
            getString(R.string.interval_30_minutes),
            getString(R.string.interval_1_hour)
        )
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter
        
        binding.spinnerInterval.setSelection(1)
    }

    private fun setupRecyclerViews() {
        appsAdapter = AppListAdapter { appInfo ->
            viewModel.toggleAppSelection(appInfo)
        }
        
        binding.recyclerViewApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appsAdapter
            setHasFixedSize(true)
        }

        monitoredAdapter = MonitoredAppsSimpleAdapter { app ->
            viewModel.removeMonitoredApp(app)
        }
        
        binding.recyclerViewMonitoredApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = monitoredAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lifecycleScope.launch {
                    delay(300)
                    viewModel.filterApps(s?.toString() ?: "")
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        // Observador para el estado de carga - AHORA USA EL CONTENEDOR COMPLETO
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.loadingContainer.visibility = View.VISIBLE
                binding.recyclerViewApps.visibility = View.GONE
            } else {
                binding.loadingContainer.visibility = View.GONE
                binding.recyclerViewApps.visibility = View.VISIBLE
            }
        }
        
        viewModel.filteredApps.observe(viewLifecycleOwner) { apps ->
            appsAdapter.submitList(apps)
        }
        
        viewModel.selectedApps.observe(viewLifecycleOwner) { selectedCount ->
            binding.buttonAddSelected.text = getString(R.string.add_selected_with_count, selectedCount)
        }

        viewModel.monitoredApps.observe(viewLifecycleOwner) { monitoredApps ->
            monitoredAdapter.submitList(monitoredApps)
            
            if (monitoredApps.isEmpty()) {
                binding.tvEmptyMonitored.visibility = View.VISIBLE
                binding.recyclerViewMonitoredApps.visibility = View.GONE
            } else {
                binding.tvEmptyMonitored.visibility = View.GONE
                binding.recyclerViewMonitoredApps.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        binding.buttonAddSelected.setOnClickListener {
            val selectedCount = viewModel.selectedApps.value ?: 0
            if (selectedCount == 0) {
                Toast.makeText(requireContext(), R.string.select_at_least_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val interval = when (binding.spinnerInterval.selectedItemPosition) {
                0 -> Constants.INTERVAL_10_SECONDS
                1 -> Constants.INTERVAL_1_MINUTE
                2 -> Constants.INTERVAL_5_MINUTES
                3 -> Constants.INTERVAL_15_MINUTES
                4 -> Constants.INTERVAL_30_MINUTES
                5 -> Constants.INTERVAL_1_HOUR
                else -> Constants.INTERVAL_1_MINUTE
            }
            
            viewModel.addSelectedAppsToMonitor(interval)
            Toast.makeText(requireContext(), R.string.apps_added, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}