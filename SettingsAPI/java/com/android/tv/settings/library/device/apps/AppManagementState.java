/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.library.device.apps;

import static android.content.pm.ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

import static com.android.tv.settings.library.ManagerUtil.STATE_APP_MANAGEMENT;
import static com.android.tv.settings.library.device.apps.EnableDisablePreferenceController.KEY_ENABLE_DISABLE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

/** State to handle app management settings screen. */
public class AppManagementState extends PreferenceControllerState {
    private static final String TAG = "AppManagementState";
    // Intent action implemented by apps that have open source licenses to display under settings
    private static final String VIEW_LICENSES_ACTION = "com.android.tv.settings.VIEW_LICENSES";
    private static final String ARG_PACKAGE_NAME = "packageName";

    private static final String KEY_VERSION = "version";
    private static final String KEY_OPEN = "open";
    private static final String KEY_LICENSES = "licenses";
    private static final String KEY_PERMISSIONS = "permissions";

    // Result code identifiers
    private static final int REQUEST_UNINSTALL = 1;
    private static final int REQUEST_MANAGE_SPACE = 2;
    private static final int REQUEST_UNINSTALL_UPDATES = 3;

    private PackageManager mPackageManager;
    private String mPackageName;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ApplicationsState.AppEntry mEntry;
    private final ApplicationsState.Callbacks mCallbacks = new ApplicationsStateCallbacks();

    private ForceStopPreferenceController mForceStopPreferenceController;
    private UninstallPreferenceController mUninstallPreferenceController;
    private EnableDisablePreferenceController mEnableDisablePreferenceController;
    private AppStoragePreferenceController mAppStoragePreferenceController;
    private ClearDataPreferenceController mClearDataPreferenceController;
    private ClearCachePreferenceController mClearCachePreferenceController;
    private ClearDefaultsPreferenceController mClearDefaultsPreferenceController;
    private NotificationsPreferenceController mNotificationsPreferenceController;

    public AppManagementState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        mPackageName = extras.getString(ARG_PACKAGE_NAME);

        Activity activity = (Activity) mContext;
        mPackageManager = activity.getPackageManager();
        mApplicationsState = ApplicationsState.getInstance(activity.getApplication());
        mSession = mApplicationsState.newSession(mCallbacks, getLifecycle());
        mEntry = mApplicationsState.getEntry(mPackageName, UserHandle.myUserId());
        super.onCreate(extras);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mEntry == null) {
            Log.w(TAG, "App not found, trying to bail out");
            mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
        }

        if (mClearDefaultsPreferenceController != null) {
            mClearDefaultsPreferenceController.refresh();
        }
        if (mEnableDisablePreferenceController != null) {
            mEnableDisablePreferenceController.refresh();
        }
    }


    @Override
    public int getStateIdentifier() {
        return STATE_APP_MANAGEMENT;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        mForceStopPreferenceController = new ForceStopPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mUninstallPreferenceController = new UninstallPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mEnableDisablePreferenceController = new EnableDisablePreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mAppStoragePreferenceController = new AppStoragePreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mClearDataPreferenceController = new ClearDataPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mClearCachePreferenceController = new ClearCachePreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mClearDefaultsPreferenceController = new ClearDefaultsPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        mNotificationsPreferenceController = new NotificationsPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry);
        List<AbstractPreferenceController> list = new ArrayList<>();
        list.add(mForceStopPreferenceController);
        list.add(mUninstallPreferenceController);
        list.add(mEnableDisablePreferenceController);
        list.add(mAppStoragePreferenceController);
        list.add(mClearCachePreferenceController);
        list.add(mClearDataPreferenceController);
        list.add(mClearDefaultsPreferenceController);
        list.add(mNotificationsPreferenceController);
        return list;
    }

    @Override
    public void onPreferenceTreeClick(String key, boolean status) {
        if (KEY_ENABLE_DISABLE.equals(key)) {
            // disable the preference to prevent double clicking
            mEnableDisablePreferenceController.setEnabled(false);
        }
        super.onPreferenceTreeClick(key, status);
    }

    private void updatePrefs() {
        // Version
        PreferenceCompat versionPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_VERSION);
        if (versionPreference == null) {
            versionPreference.setSelectable(false);
        }
        versionPreference.setTitle(
                ResourcesUtil.getString(mContext, "device_apps_app_management_version",
                        mEntry.getVersion(mContext)));
        versionPreference.setSummary(mPackageName);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), versionPreference);

        // Open
        PreferenceCompat openPreference = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_OPEN);
        Intent appLaunchIntent =
                mPackageManager.getLeanbackLaunchIntentForPackage(mEntry.info.packageName);
        if (appLaunchIntent == null) {
            appLaunchIntent = mPackageManager.getLaunchIntentForPackage(mEntry.info.packageName);
        }
        if (appLaunchIntent != null) {
            openPreference.setIntent(appLaunchIntent);
            openPreference.setTitle(
                    ResourcesUtil.getString(mContext, "device_apps_app_management_open"));
            openPreference.setVisible(true);
        } else {
            openPreference.setVisible(false);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), openPreference);

        // Force stop
        if (mForceStopPreferenceController != null) {
            mForceStopPreferenceController.setEntry(mEntry);
        }

        // Uninstall
        if (mUninstallPreferenceController != null) {
            mUninstallPreferenceController.setEntry(mEntry);
        }

        // Disable/Enable
        if (mEnableDisablePreferenceController != null) {
            mEnableDisablePreferenceController.setEntry(mEntry);
            mEnableDisablePreferenceController.setEnabled(true);
        }

        // Storage used
        if (mAppStoragePreferenceController != null) {
            mAppStoragePreferenceController.setEntry(mEntry);
        }

        // Clear data
        if (clearDataAllowed() && mClearDefaultsPreferenceController != null) {
            mClearDataPreferenceController.setEntry(mEntry);
        }

        // Clear cache
        if (mClearCachePreferenceController != null) {
            mClearCachePreferenceController.setEntry(mEntry);
        }

        // Clear defaults
        if (mClearDefaultsPreferenceController != null) {
            mClearDefaultsPreferenceController.setEntry(mEntry);
        }

        // Notifications
        if (mNotificationsPreferenceController == null) {
            mNotificationsPreferenceController.setEntry(mEntry);
        }

        // Open Source Licenses
        PreferenceCompat licensesPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_LICENSES);
        // Check if app has open source licenses to display
        Intent licenseIntent = new Intent(VIEW_LICENSES_ACTION);
        licenseIntent.setPackage(mEntry.info.packageName);
        ResolveInfo resolveInfo = resolveIntent(licenseIntent);
        if (resolveInfo == null) {
            licensesPreference.setVisible(false);
        } else {
            Intent intent = new Intent(licenseIntent);
            intent.setClassName(resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name);
            licensesPreference.setIntent(intent);
            licensesPreference.setTitle(ResourcesUtil.getString(mContext,
                    "device_apps_app_management_licenses"));
            licensesPreference.setVisible(true);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), licensesPreference);

        // Permissions
        PreferenceCompat permissionsPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_PERMISSIONS);
        permissionsPreference.setTitle(ResourcesUtil.getString(mContext,
                "device_apps_app_management_permissions"));
        permissionsPreference.setIntent(new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName));
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), permissionsPreference);
    }

    private class ApplicationsStateCallbacks implements ApplicationsState.Callbacks {

        @Override
        public void onRunningStateChanged(boolean running) {
            if (mForceStopPreferenceController != null) {
                mForceStopPreferenceController.refresh();
            }
        }

        @Override
        public void onPackageListChanged() {
            if (mEntry == null || mEntry.info == null) {
                return;
            }
            final int userId = UserHandle.getUserId(mEntry.info.uid);
            mEntry = mApplicationsState.getEntry(mPackageName, userId);
            if (mEntry == null) {
                mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
            }
            updatePrefs();
        }

        @Override
        public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
        }

        @Override
        public void onPackageIconChanged() {
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            if (mAppStoragePreferenceController == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreferenceController.refresh();
            if (mClearCachePreferenceController != null) {
                mClearCachePreferenceController.refresh();
            }

            if (mClearDataPreferenceController != null) {
                mClearDataPreferenceController.refresh();
            }
        }

        @Override
        public void onAllSizesComputed() {
            if (mAppStoragePreferenceController == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreferenceController.refresh();
            if (mClearCachePreferenceController != null) {
                mClearCachePreferenceController.refresh();
            }

            if (mClearDataPreferenceController != null) {
                mClearDataPreferenceController.refresh();
            }
        }

        @Override
        public void onLauncherInfoChanged() {
            updatePrefs();
        }

        @Override
        public void onLoadEntriesCompleted() {
            mEntry = mApplicationsState.getEntry(mPackageName, UserHandle.myUserId());
            updatePrefs();
            if (mAppStoragePreferenceController == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreferenceController.refresh();
            if (mClearCachePreferenceController != null) {
                mClearCachePreferenceController.refresh();
            }

            if (mClearDataPreferenceController != null) {
                mClearDataPreferenceController.refresh();
            }
        }
    }

    private ResolveInfo resolveIntent(Intent intent) {
        List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(intent, 0);
        return (resolveInfos == null || resolveInfos.size() <= 0) ? null : resolveInfos.get(0);
    }

    /**
     * Clearing data can only be disabled for system apps. For all non-system apps it is enabled.
     * System apps disable it explicitly via the android:allowClearUserData tag.
     **/
    private boolean clearDataAllowed() {
        boolean sysApp = (mEntry.info.flags & FLAG_SYSTEM) == FLAG_SYSTEM;
        boolean allowClearData =
                (mEntry.info.flags & FLAG_ALLOW_CLEAR_USER_DATA) == FLAG_ALLOW_CLEAR_USER_DATA;
        return !sysApp || allowClearData;
    }
}
