package org.microg.gms.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.microg.gms.auth.AuthConstants
import org.microg.gms.auth.login.LoginActivity
import org.microg.gms.people.DatabaseHelper
import org.microg.gms.people.PeopleManager
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView

class AccountsFragment : PreferenceFragmentCompat() {

    private val tag = AccountsFragment::class.java.simpleName

    private lateinit var fab: ExtendedFloatingActionButton

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_accounts)
        refreshAccountSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))

        addAccountFab()
        setupPreferenceListeners()
    }

    override fun onResume() {
        super.onResume()
        fab.show()
        refreshAccountSettings()
    }

    override fun onStop() {
        super.onStop()
        fab.hide()
    }

    private fun setupPreferenceListeners() {
        findPreference<Preference>("pref_manage_accounts")?.setOnPreferenceClickListener {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            startActivitySafelyIntent(intent, "Failed to launch sync in device settings")
            true
        }

        findPreference<Preference>("pref_privacy")?.setOnPreferenceClickListener {
            startActivitySafely(
                PrivacySettingsActivity::class.java, "Failed to launch privacy activity"
            )
            true
        }

        findPreference<Preference>("pref_manage_history")?.setOnPreferenceClickListener {
            openUrl("https://myactivity.google.com/product/youtube")
            true
        }

        findPreference<Preference>("pref_your_data")?.setOnPreferenceClickListener {
            openUrl("https://myaccount.google.com/yourdata/youtube")
            true
        }
    }

    private fun startActivitySafelyIntent(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(tag, errorMessage, e)
            showSnackbar(errorMessage)
        }
    }

    private fun startActivitySafely(activityClass: Class<*>, errorMessage: String) {
        val intent = Intent(requireContext(), activityClass)
        startActivitySafelyIntent(intent, errorMessage)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivitySafelyIntent(intent, "Failed to open URL: $url")
    }

    private fun addAccountFab() {
        fab = requireActivity().findViewById(R.id.preference_fab)
        fab.text = getString(R.string.pref_accounts_add_account_title)
        fab.setIconResource(R.drawable.ic_add_new_account)
        fab.setOnClickListener {
            startActivitySafely(LoginActivity::class.java, "Failed to launch login activity")
        }
        fab.show()
    }

    private fun refreshAccountSettings() {
        val context = requireContext()
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)

        clearAccountPreferences()

        val preferenceCategory = findPreference<PreferenceCategory>("prefcat_current_accounts")
        val accountsCategoryVisible = accounts.isNotEmpty()

        preferenceCategory?.let {
            it.isVisible = accountsCategoryVisible
            if (accountsCategoryVisible) {
                lifecycleScope.launch(Dispatchers.Main) {
                    accounts.forEach { account ->
                        val photo = PeopleManager.getOwnerAvatarBitmap(context, account.name, false)
                        val newPreference = Preference(requireContext()).apply {
                            title = getDisplayName(account)
                            summary = account.name
                            icon = getCircleBitmapDrawable(photo)
                            key = "account:${account.name}"
                            order = 0

                            setOnPreferenceClickListener {
                                showAccountRemovalDialog(account.name)
                                true
                            }
                        }

                        if (preferenceCategory.findPreference<Preference>(newPreference.key) == null) {
                            if (photo == null) {
                                withContext(Dispatchers.IO) {
                                    PeopleManager.getOwnerAvatarBitmap(context, account.name, true)
                                }?.let { newPreference.icon = getCircleBitmapDrawable(it) }
                            }
                            preferenceCategory.addPreference(newPreference)
                        }
                    }
                }
            }
        }
    }

    private fun clearAccountPreferences() {
        findPreference<PreferenceCategory>("prefcat_current_accounts")?.removeAll()
    }

    private fun showAccountRemovalDialog(accountName: String) {
        val account = Account(accountName, AuthConstants.DEFAULT_ACCOUNT_TYPE)

        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_remove_account, null)

        val avatarView = dialogView.findViewById<ShapeableImageView>(R.id.account_avatar)
        val nameView = dialogView.findViewById<MaterialTextView>(R.id.account_name)
        val emailView = dialogView.findViewById<MaterialTextView>(R.id.account_email)
        val dialogTitle = dialogView.findViewById<MaterialTextView>(R.id.dialog_title)
        val messageView = dialogView.findViewById<MaterialTextView>(R.id.dialog_message)
        val positiveButton = dialogView.findViewById<MaterialButton>(R.id.positive_button)
        val negativeButton = dialogView.findViewById<MaterialButton>(R.id.negative_button)

        nameView.text = getDisplayName(account) ?: accountName
        emailView.text = accountName
        dialogTitle.text = getString(R.string.dialog_title_remove_account)
        messageView.text = getString(R.string.dialog_message_remove_account)
        positiveButton.text = getString(R.string.dialog_confirm_button)
        negativeButton.text = getString(R.string.dialog_cancel_button)

        lifecycleScope.launch(Dispatchers.IO) {
            PeopleManager.getOwnerAvatarBitmap(requireContext(), accountName, true)?.let { bmp ->
                val circular = RoundedBitmapDrawableFactory.create(resources, bmp).apply {
                    isCircular = true
                }
                withContext(Dispatchers.Main) {
                    avatarView.setImageDrawable(circular)
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()

        positiveButton.setOnClickListener {
            removeAccount(accountName)
            dialog.dismiss()
        }
        negativeButton.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun removeAccount(accountName: String) {
        val rootView = view ?: return
        val accountManager = AccountManager.get(requireContext())
        val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)
        val accountToRemove = accounts.firstOrNull { it.name == accountName }

        accountToRemove?.let {
            val snackbar = Snackbar.make(
                rootView,
                getString(R.string.snackbar_remove_account, accountName),
                Snackbar.LENGTH_LONG
            )

            var cancelRemoval = false

            snackbar.setAction(getString(R.string.snackbar_undo_button)) {
                cancelRemoval = true
            }

            snackbar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (!cancelRemoval) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val removedSuccessfully = accountManager.removeAccountExplicitly(it)
                                if (removedSuccessfully) {
                                    withContext(Dispatchers.Main) {
                                        refreshAccountSettings()
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            })
            snackbar.show()
        }
    }

    private fun getDisplayName(account: Account): String? {
        val databaseHelper = DatabaseHelper(requireContext())
        val cursor = databaseHelper.getOwner(account.name)
        return try {
            if (cursor.moveToNext()) {
                cursor.getColumnIndex("display_name").takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }?.takeIf { it.isNotBlank() }
            } else null
        } finally {
            cursor.close()
            databaseHelper.close()
        }
    }

    private fun getCircleBitmapDrawable(bitmap: Bitmap?) = bitmap?.let {
        RoundedBitmapDrawableFactory.create(resources, it).apply {
            isCircular = true
        }
    }

    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }
}