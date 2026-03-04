package com.gnzalobnites.appsusagemonitor.ui.stats

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.gnzalobnites.appsusagemonitor.MyApplication
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.data.database.DailyUsageStats
import com.gnzalobnites.appsusagemonitor.databinding.FragmentStatisticsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModels {
        StatisticsViewModelFactory(
            requireActivity().application,
            MyApplication.database.usageSessionDao()
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupChart()
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnPreviousWeek.visibility = View.GONE
        binding.btnNextWeek.visibility = View.GONE

        binding.spinnerApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val packageName = parent?.getItemAtPosition(position) as? String
                packageName?.let { viewModel.selectApp(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.availableApps.collectLatest { apps ->
                val adapter = AppSpinnerAdapter(requireContext(), apps)
                binding.spinnerApps.adapter = adapter
            }
        }

        lifecycleScope.launch {
            viewModel.weekRangeLabel.collectLatest { label ->
                binding.textWeekRange.text = label
            }
        }

        lifecycleScope.launch {
            viewModel.weeklyStats.collectLatest { stats ->
                updateChart(stats)
            }
        }
    }

    private fun setupChart() {
        binding.chartUsage.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
            }

            axisLeft.apply {
                textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
                axisMinimum = 0f
                granularity = 1f
                setLabelCount(5, false)
                valueFormatter = TimeValueFormatter()
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun updateChart(dailyStats: List<DailyUsageStats>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        
        val sdf = SimpleDateFormat(getString(R.string.chart_date_format), Locale.getDefault())
        
        dailyStats.forEachIndexed { index, stat ->
            entries.add(BarEntry(index.toFloat(), (stat.totalDuration / 60000f)))
            labels.add(sdf.format(Date(stat.dayTimestamp)))
        }

        val dataSet = BarDataSet(entries, getString(R.string.chart_usage_label)).apply {
            color = Color.parseColor("#2196F3")
            valueTextColor = if (isDarkMode()) Color.WHITE else Color.BLACK
            valueTextSize = 10f
            valueFormatter = TimeValueFormatter()
        }

        binding.chartUsage.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            invalidate()
        }
    }

    private fun isDarkMode(): Boolean {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}