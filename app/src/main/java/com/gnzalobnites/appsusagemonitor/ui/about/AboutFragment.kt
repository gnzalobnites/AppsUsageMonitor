package com.gnzalobnites.appsusagemonitor.ui.about

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gnzalobnites.appsusagemonitor.R
import com.gnzalobnites.appsusagemonitor.databinding.FragmentAboutBinding
import com.gnzalobnites.appsusagemonitor.utils.AppUpdater
import com.gnzalobnites.appsusagemonitor.utils.UpdateManager
import com.gnzalobnites.appsusagemonitor.utils.UpdateInfo  // <-- IMPORTACIÓN AÑADIDA
import kotlinx.coroutines.launch

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private var appUpdater: AppUpdater? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.textVersion.text = getString(R.string.about_version_format, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            binding.textVersion.text = getString(R.string.about_version_placeholder)
        }

        binding.btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.developer_email)}")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_support))
            }
            startActivity(intent)
        }

        binding.btnGithub.setOnClickListener {
            openUrl(getString(R.string.github_url))
        }

        binding.btnCoffee.setOnClickListener {
            openUrl(getString(R.string.buy_me_coffee_url))
        }
        
        // Botón de buscar actualizaciones
        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdatesManually()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    // Comprobación manual de actualizaciones
    private fun checkForUpdatesManually() {
        // Deshabilitar el botón temporalmente para evitar spam de clics
        binding.btnCheckUpdates.isEnabled = false
        binding.btnCheckUpdates.text = getString(R.string.update_checking)

        lifecycleScope.launch {
            try {
                val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                val currentVersion = packageInfo.versionName

                val updateManager = UpdateManager()
                val updateInfo = updateManager.checkForUpdates(currentVersion)

                if (updateInfo != null) {
                    // Hay actualización
                    showUpdateAvailableDialog(updateInfo)
                } else {
                    // No hay actualización
                    Toast.makeText(requireContext(), R.string.update_no_update_available, Toast.LENGTH_SHORT).show()
                    // Restaurar el botón
                    binding.btnCheckUpdates.isEnabled = true
                    binding.btnCheckUpdates.text = getString(R.string.update_check_button)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.update_check_error, Toast.LENGTH_SHORT).show()
                // Restaurar el botón
                binding.btnCheckUpdates.isEnabled = true
                binding.btnCheckUpdates.text = getString(R.string.update_check_button)
            }
        }
    }

    // Diálogo para mostrar que hay actualización (desde AboutFragment)
    private fun showUpdateAvailableDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.update_dialog_title)
            .setMessage(getString(R.string.update_dialog_message, updateInfo.versionName))
            .setPositiveButton(R.string.update_dialog_download) { _, _ ->
                appUpdater = AppUpdater(requireContext())
                appUpdater?.downloadAndInstall(updateInfo.downloadUrl)
                binding.btnCheckUpdates.isEnabled = true
                binding.btnCheckUpdates.text = getString(R.string.update_check_button)
            }
            .setNegativeButton(R.string.update_dialog_later) { _, _ ->
                binding.btnCheckUpdates.isEnabled = true
                binding.btnCheckUpdates.text = getString(R.string.update_check_button)
            }
            .setOnCancelListener {
                binding.btnCheckUpdates.isEnabled = true
                binding.btnCheckUpdates.text = getString(R.string.update_check_button)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appUpdater?.cleanup()
        _binding = null
    }
}