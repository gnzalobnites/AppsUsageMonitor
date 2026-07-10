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
        
        if (!isAdded || context == null) return
        
        setupSpinner()
        setupRecyclerViews()
        setupSearch()
        setupObservers()
        setupListeners()
        
        showLoading(true)
        viewModel.loadInstalledApps()
    }

    private fun setupSpinner() {
        if (!isAdded || context == null) return
        
        val goals = resources.getStringArray(R.array.goal_entries).toList()
        
        val ctx = requireContext()
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, goals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter
        // Seleccionar 5 minutos por defecto (índice 2)
        binding.spinnerInterval.setSelection(2)
    }

    private fun setupRecyclerViews() {
        if (!isAdded || context == null) return
        
        val ctx = requireContext()
        
        appsAdapter = AppListAdapter { appInfo ->
            viewModel.toggleAppSelection(appInfo)
        }
        
        binding.recyclerViewApps.apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = appsAdapter
            setHasFixedSize(true)
        }

        monitoredAdapter = MonitoredAppsSimpleAdapter { app ->
            viewModel.removeMonitoredApp(app)
        }
        
        binding.recyclerViewMonitoredApps.apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = monitoredAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun setupSearch() {
        if (!isAdded || context == null) return
        
        binding.editSearch.setOnTouchListener { _, event ->
            val DRAWABLE_END = 2
            
            if (event.action == MotionEvent.ACTION_UP) {
                val endDrawable = binding.editSearch.compoundDrawablesRelative[DRAWABLE_END]
                if (endDrawable != null) {
                    val touchableArea = binding.editSearch.width - binding.editSearch.paddingEnd - endDrawable.intrinsicWidth
                    
                    if (event.x >= touchableArea) {
                        binding.editSearch.text.clear()
                        binding.editSearch.clearFocus()
                        
                        if (isAdded && context != null) {
                            val imm = requireContext().getSystemService(InputMethodManager::class.java)
                            imm.hideSoftInputFromWindow(binding.editSearch.windowToken, 0)
                        }
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
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredApps.collect { apps ->
                appsAdapter.submitList(apps)
                showLoading(false)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
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
        if (!isAdded || context == null) return
        
        binding.editSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.bottomSectionContainer.visibility = if (hasFocus) View.GONE else View.VISIBLE
        }

        binding.recyclerViewApps.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING && binding.editSearch.hasFocus()) {
                    binding.editSearch.clearFocus()
                    
                    if (isAdded && context != null) {
                        val imm = requireContext().getSystemService(InputMethodManager::class.java)
                        imm.hideSoftInputFromWindow(recyclerView.windowToken, 0)
                    }
                }
            }
        })

        binding.buttonAddSelected.setOnClickListener {
            if (!isAdded || context == null) return@setOnClickListener
            
            binding.editSearch.clearFocus()
            
            val ctx = requireContext()
            val imm = ctx.getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(it.windowToken, 0)

            val selectedCount = viewModel.selectedApps.value
            if (selectedCount == 0) {
                Toast.makeText(ctx, R.string.select_at_least_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Obtener la meta en minutos del Spinner
            val goalMinutes = when (binding.spinnerInterval.selectedItemPosition) {
                0 -> 1
                1 -> 2
                2 -> 5
                3 -> 10
                4 -> 15
                5 -> 30
                6 -> 60
                else -> 5
            }
            
            viewModel.addSelectedAppsToMonitor(goalMinutes)
            Toast.makeText(ctx, R.string.apps_added, Toast.LENGTH_SHORT).show()
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
        searchJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}