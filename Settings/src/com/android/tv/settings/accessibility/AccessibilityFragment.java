/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.settings.accessibility;

import static android.content.Context.ACCESSIBILITY_SERVICE;

import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.accessibility.AccessibilityUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.List;
import java.util.Set;

/**
 * Fragment for Accessibility settings
 */
@Keep
public class AccessibilityFragment extends SettingsPreferenceFragment {
    private static final String TOGGLE_HIGH_TEXT_CONTRAST_KEY = "toggle_high_text_contrast";
    private static final String TOGGLE_AUDIO_DESCRIPTION_KEY = "toggle_audio_description";
    private static final String ACCESSIBILITY_SERVICES_KEY = "system_accessibility_services";

    private PreferenceGroup mServicesPref;
    private AccessibilityManager.AccessibilityStateChangeListener
            mAccessibilityStateChangeListener = enabled -> refreshServices(mServicesPref);

    /**
     * Create a new instance of the fragment
     * @return New fragment instance
     */
    public static AccessibilityFragment newInstance() {
        return new AccessibilityFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mServicesPref != null) {
            refreshServices(mServicesPref);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(ACCESSIBILITY_SERVICE);
        if (am != null && mServicesPref != null) {
            am.removeAccessibilityStateChangeListener(mAccessibilityStateChangeListener);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility, null);

        final TwoStatePreference highContrastPreference =
                (TwoStatePreference) findPreference(TOGGLE_HIGH_TEXT_CONTRAST_KEY);
        highContrastPreference.setChecked(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED, 0) == 1);

        final TwoStatePreference audioDescriptionPreference =
                (TwoStatePreference) findPreference(TOGGLE_AUDIO_DESCRIPTION_KEY);
        audioDescriptionPreference.setChecked(Settings.Secure.getInt(
                getContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT, 0) == 1);

        mServicesPref = (PreferenceGroup) findPreference(ACCESSIBILITY_SERVICES_KEY);
        if (mServicesPref != null) {
            refreshServices(mServicesPref);
            AccessibilityManager am = (AccessibilityManager)
                    getContext().getSystemService(ACCESSIBILITY_SERVICE);
            if (am != null) {
                am.addAccessibilityStateChangeListener(mAccessibilityStateChangeListener);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), TOGGLE_HIGH_TEXT_CONTRAST_KEY)) {
            logToggleInteracted(
                    TvSettingsEnums.SYSTEM_A11Y_HIGH_CONTRAST_TEXT,
                    ((SwitchPreference) preference).isChecked());
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED,
                    (((SwitchPreference) preference).isChecked() ? 1 : 0));
            return true;
        } else if (TextUtils.equals(preference.getKey(), TOGGLE_AUDIO_DESCRIPTION_KEY)) {
            logToggleInteracted(
                    TvSettingsEnums.SYSTEM_A11Y_AUDIO_DESCRIPTION,
                    ((SwitchPreference) preference).isChecked());
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT,
                    (((SwitchPreference) preference).isChecked() ? 1 : 0));
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    private void refreshServices(PreferenceGroup group) {
        DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);
        final List<AccessibilityServiceInfo> installedServiceInfos =
                getActivity().getSystemService(AccessibilityManager.class)
                        .getInstalledAccessibilityServiceList();
        final Set<ComponentName> enabledServices =
                AccessibilityUtils.getEnabledServicesFromSettings(getActivity());
        final List<String> permittedServices = dpm.getPermittedAccessibilityServices(
                UserHandle.myUserId());

        final boolean accessibilityEnabled = Settings.Secure.getInt(
                getActivity().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;

        for (final AccessibilityServiceInfo accInfo : installedServiceInfos) {
            final ServiceInfo serviceInfo = accInfo.getResolveInfo().serviceInfo;
            final ComponentName componentName = new ComponentName(serviceInfo.packageName,
                    serviceInfo.name);

            final boolean serviceEnabled = accessibilityEnabled
                    && enabledServices.contains(componentName);
            // permittedServices null means all accessibility services are allowed.
            final boolean serviceAllowed = permittedServices == null
                    || permittedServices.contains(serviceInfo.packageName);

            final String title = accInfo.getResolveInfo()
                    .loadLabel(getActivity().getPackageManager()).toString();

            final String key = "ServicePref:" + componentName.flattenToString();
            RestrictedPreference servicePref = findPreference(key);
            if (servicePref == null) {
                servicePref = new RestrictedPreference(group.getContext());
                servicePref.setKey(key);
            }
            servicePref.setTitle(title);
            servicePref.setSummary(serviceEnabled ? R.string.settings_on : R.string.settings_off);
            AccessibilityServiceFragment.prepareArgs(servicePref.getExtras(),
                    serviceInfo.packageName,
                    serviceInfo.name,
                    accInfo.getSettingsActivityName(),
                    title);

            if (serviceAllowed || serviceEnabled) {
                servicePref.setEnabled(true);
                servicePref.setFragment(AccessibilityServiceFragment.class.getName());
            } else {
                // Disable accessibility service that are not permitted.
                final EnforcedAdmin admin =
                        RestrictedLockUtilsInternal.checkIfAccessibilityServiceDisallowed(
                                getContext(), serviceInfo.packageName, UserHandle.myUserId());
                if (admin != null) {
                    servicePref.setDisabledByAdmin(admin);
                } else {
                    servicePref.setEnabled(false);
                }
                servicePref.setFragment(null);
            }

            group.addPreference(servicePref);
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_A11Y;
    }
}
