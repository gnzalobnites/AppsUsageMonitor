package com.gnzalobnites.appsusagemonitor.ui.about

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gnzalobnites.appsusagemonitor.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mostrar versión de forma segura usando PackageManager
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.textVersion.text = "Versión ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            binding.textVersion.text = "Versión 1.0.0"
        }

        binding.btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:benitesgonzalogaston@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Apps Usage Monitor Support")
            }
            startActivity(intent)
        }

        binding.btnGithub.setOnClickListener {
            openUrl("https://github.com/gnzalobnites/AppsUsageMonitor/tree/main")
        }

        binding.btnCoffee.setOnClickListener {
            openUrl("https://buymeacoffee.com/gnzbenitesh")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}