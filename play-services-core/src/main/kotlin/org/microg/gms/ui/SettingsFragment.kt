/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.R
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import org.microg.gms.checkin.CheckinPreferences
import org.microg.gms.gcm.GcmDatabase
import org.microg.gms.gcm.GcmPrefs
import org.microg.gms.ui.settings.SettingsProvider
import org.microg.gms.ui.settings.getAllSettingsProviders
import org.microg.tools.ui.ResourceSettingsFragment

class SettingsFragment : ResourceSettingsFragment() {

    companion object {
        private const val TAG = "SettingsFragment"

        const val PREF_ABOUT = "pref_about"
        const val PREF_GCM = "pref_gcm"
        const val PREF_PRIVACY = "pref_privacy"
        const val PREF_CHECKIN = "pref_checkin"
        const val PREF_ACCOUNTS = "pref_accounts"
        const val PREF_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"
        const val PREF_DEVELOPER = "pref_developer"
        const val PREF_GITHUB = "pref_github"
        const val PREF_IGNORE_BATTERY_OPTIMIZATION = "pref_ignore_battery_optimization"
    }

    private val createdPreferences = mutableListOf<Preference>()

    init {
        preferencesResource = R.xml.preferences_start
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setupStaticPreferenceClickListeners()
        updateAboutSummary()
        loadStaticEntries()
        updateBatteryOptimizationPreferenceVisibility()
    }

    override fun onResume() {
        super.onResume()

        requireActivity().findViewById<ExtendedFloatingActionButton>(R.id.preference_fab)?.visibility = View.GONE

        updateBatteryOptimizationPreferenceVisibility()
        updateHideLauncherIconSwitchState()

        updateGcmSummary()
        updateCheckinSummary()
        updateDynamicEntries()
    }

    private fun setupStaticPreferenceClickListeners() {
        findPreference<Preference>(PREF_ACCOUNTS)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.accountManagerFragment)
            true
        }

        findPreference<Preference>(PREF_CHECKIN)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openCheckinSettings)
            true
        }

        findPreference<Preference>(PREF_GCM)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openGcmSettings)
            true
        }

        findPreference<Preference>(PREF_PRIVACY)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.privacyFragment)
            true
        }

        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.setOnPreferenceChangeListener { _, newValue ->
            toggleActivityVisibility(MainSettingsActivity::class.java, !(newValue as Boolean))
            true
        }

        findPreference<Preference>(PREF_DEVELOPER)?.setOnPreferenceClickListener {
            openLink(getString(R.string.developer_link))
            true
        }

        findPreference<Preference>(PREF_GITHUB)?.setOnPreferenceClickListener {
            openLink(getString(R.string.github_link))
            true
        }

        findPreference<Preference>(PREF_ABOUT)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openAbout)
            true
        }
    }

    private fun updateAboutSummary() {
        findPreference<Preference>(PREF_ABOUT)?.summary =
            getString(org.microg.tools.ui.R.string.about_version_str, AboutFragment.getSelfVersion(context))
    }

    private fun loadStaticEntries() {
        getAllSettingsProviders(requireContext())
            .flatMap { it.getEntriesStatic(requireContext()) }
            .forEach { entry -> entry.createPreference() }
    }

    private fun updateDynamicEntries() {
        lifecycleScope.launch {
            val entries = getAllSettingsProviders(requireContext())
                .flatMap { it.getEntriesDynamic(requireContext()) }

            createdPreferences.forEach { preference ->
                if (entries.none { it.key == preference.key }) preference.isVisible = false
            }

            entries.forEach { entry ->
                val preference = createdPreferences.find { it.key == entry.key }
                if (preference != null) preference.fillFromEntry(entry)
                else entry.createPreference()
            }
        }
    }

    private val Context.isIgnoringBatteryOptimizations: Boolean
        get() = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(
            packageName
        ) == true

    private fun updateBatteryOptimizationPreferenceVisibility() {
        findPreference<Preference>(PREF_IGNORE_BATTERY_OPTIMIZATION)?.apply {
            isVisible = !requireContext().isIgnoringBatteryOptimizations
            setOnPreferenceClickListener {
                requestBatteryOptimizationPermission()
                true
            }
        }
    }

    private fun requestBatteryOptimizationPermission() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Not possible request battery optimization permission", e)
        }
    }

    private fun toggleActivityVisibility(activityClass: Class<*>, showActivity: Boolean) {
        val component = ComponentName(requireContext(), activityClass)
        val newState = if (showActivity) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        requireActivity().packageManager.setComponentEnabledSetting(
            component, newState, PackageManager.DONT_KILL_APP
        )
    }

    private fun updateHideLauncherIconSwitchState() {
        val isVisible = isIconActivityVisible(MainSettingsActivity::class.java)
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.isChecked = !isVisible
    }

    private fun isIconActivityVisible(activityClass: Class<*>): Boolean {
        val component = ComponentName(requireContext(), activityClass)
        val setting = requireActivity().packageManager.getComponentEnabledSetting(component)

        return when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                try {
                    requireActivity().packageManager.getActivityInfo(component, 0).enabled
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            }
            else -> false
        }
    }

    private fun updateGcmSummary() {
        val context = requireContext()
        val pref = findPreference<Preference>(PREF_GCM) ?: return

        if (GcmPrefs.get(context).isEnabled) {
            val database = GcmDatabase(context)
            val regCount = database.registrationList.size
            database.close()

            pref.summary = context.getString(org.microg.gms.base.core.R.string.service_status_enabled_short) +
                    " - " + context.resources.getQuantityString(R.plurals.gcm_registered_apps_counter, regCount, regCount)
        } else {
            pref.setSummary(org.microg.gms.base.core.R.string.service_status_disabled_short)
        }
    }

    private fun updateCheckinSummary() {
        val summaryRes = if (CheckinPreferences.isEnabled(requireContext()))
            org.microg.gms.base.core.R.string.service_status_enabled_short
        else org.microg.gms.base.core.R.string.service_status_disabled_short

        findPreference<Preference>(PREF_CHECKIN)?.setSummary(summaryRes)
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Error opening link: $url", e)
        }
    }

    private fun SettingsProvider.Companion.Entry.createPreference(): Preference? {
        val preference = Preference(requireContext()).fillFromEntry(this)
        val categoryKey = when (group) {
            SettingsProvider.Companion.Group.HEADER -> "prefcat_header"
            SettingsProvider.Companion.Group.GOOGLE -> "prefcat_google_services"
            SettingsProvider.Companion.Group.OTHER -> "prefcat_other_services"
            SettingsProvider.Companion.Group.FOOTER -> "prefcat_footer"
        }

        return try {
            findPreference<PreferenceCategory>(categoryKey)?.addPreference(preference)?.let {
                if (it) createdPreferences.add(preference)
                preference
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed adding preference $key", e)
            null
        }
    }

    private fun Preference.fillFromEntry(entry: SettingsProvider.Companion.Entry): Preference {
        key = entry.key
        title = entry.title
        summary = entry.summary
        icon = entry.icon
        isPersistent = false
        isVisible = true
        setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), entry.navigationId)
            true
        }
        return this
    }
}