package com.gnzalobnites.appsusagemonitor.ui.selection

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.databinding.FragmentAppSelectionBinding
import com.gnzalobnites.appsusagemonitor.ui.adapters.AppListAdapter
import com.gnzalobnites.appsusagemonitor.ui.adapters.MonitoredAppsSimpleAdapter
import com.gnzalobnites.appsusagemonitor.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppSelectionFragment : Fragment() {
    
    private var _binding: FragmentAppSelectionBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AppSelectionViewModel by viewModels()
    private lateinit var appsAdapter: AppListAdapter
    private lateinit var monitoredAdapter: MonitoredAppsSimpleAdapter
    
    private var searchJob: Job? = null

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
        
        showLoading(true)
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
        binding.editSearch.setOnTouchListener { _, event ->
            val DRAWABLE_END = 2
            
            if (event.action == MotionEvent.ACTION_UP) {
                val endDrawable = binding.editSearch.compoundDrawablesRelative[DRAWABLE_END]
                if (endDrawable != null) {
                    val touchableArea = binding.editSearch.right - binding.editSearch.paddingEnd - endDrawable.intrinsicWidth
                    
                    if (event.rawX >= touchableArea) {
                        binding.editSearch.text.clear()
                        binding.editSearch.clearFocus()
                        val imm = requireContext().getSystemService(InputMethodManager::class.java)
                        imm.hideSoftInputFromWindow(binding.editSearch.windowToken, 0)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editSearch.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    android.R.drawable.ic_menu_search,
                    0, 
                    if (!s.isNullOrEmpty()) android.R.drawable.ic_menu_close_clear_cancel else 0, 
                    0
                )

                if (!s.isNullOrEmpty()) {
                    showLoading(true)
                } else {
                    showLoading(false)
                }

                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    viewModel.filterApps(s?.toString() ?: "")
                    if (s.isNullOrEmpty()) {
                        showLoading(false)
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.filteredApps.collect { apps ->
                appsAdapter.submitList(apps)
                showLoading(false)
            }
        }
        
        lifecycleScope.launch {
            viewModel.selectedApps.collect { selectedCount ->
                binding.buttonAddSelected.text = getString(R.string.add_selected_with_count, selectedCount)
            }
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
        binding.editSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.bottomSectionContainer.visibility = if (hasFocus) View.GONE else View.VISIBLE
        }

        binding.recyclerViewApps.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING && binding.editSearch.hasFocus()) {
                    binding.editSearch.clearFocus()
                    val imm = requireContext().getSystemService(InputMethodManager::class.java)
                    imm.hideSoftInputFromWindow(recyclerView.windowToken, 0)
                }
            }
        })

        binding.buttonAddSelected.setOnClickListener {
            binding.editSearch.clearFocus()
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(it.windowToken, 0)

            val selectedCount = viewModel.selectedApps.value
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

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.loadingContainer.visibility = View.VISIBLE
            binding.recyclerViewApps.visibility = View.GONE
        } else {
            binding.loadingContainer.visibility = View.GONE
            binding.recyclerViewApps.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancelar searchJob al pausar para ahorrar recursos
        searchJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}