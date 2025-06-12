/*
 * Copyright (C) 2022-2024 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Binder;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";

    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final String FEATURE_NEXUS_PRELOAD = "com.google.android.apps.photos.NEXUS_PRELOAD";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

    private static final Map<String, String> sPixelOneProps = Map.of(
        "PRODUCT", "sailfish",
        "DEVICE", "sailfish",
        "MANUFACTURER", "Google",
        "BRAND", "google",
        "MODEL", "Pixel",
        "FINGERPRINT", "google/sailfish/sailfish:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sPixelFeatures = Set.of(
        "PIXEL_2017_PRELOAD",
        "PIXEL_2018_PRELOAD",
        "PIXEL_2019_MIDYEAR_PRELOAD",
        "PIXEL_2019_PRELOAD",
        "PIXEL_2020_EXPERIENCE",
        "PIXEL_2020_MIDYEAR_EXPERIENCE"
    );

    private static volatile boolean sIsGms, sIsPhotos;
    private static volatile List<String> sCertifiedProps = new ArrayList<>();

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        if (sIsGms) {
            Log.d(TAG, "Spoofing json.pif for gms");
            if (loadFromJsonSetting(context))
                setCertifiedPropsForGms();
        } else if (sIsPhotos) {
            Log.d(TAG, "Spoofing Pixel 1 for Google Photos");
            sPixelOneProps.forEach((PropImitationHooks::setPropValue));
        }
    }

    private static boolean loadFromJsonSetting(Context ctx) {
        try {
            String json = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.PIF_DATA);
            JSONObject parsedProps = new JSONObject(json);
            Iterator<String> keys = parsedProps.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                String value = parsedProps.getString(key);
                sCertifiedProps.add(key + ":" + value);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "pif.json load failed", e);
            return false;
        }
        return true;
    }

    private static void setPropValue(String key, String value) {
        try {
            Log.d(TAG, "Setting prop " + key + " to " + value.toString());
            Class clazz = Build.class;
            if (key.startsWith("VERSION.")) {
                clazz = Build.VERSION.class;
                key = key.substring(8);
            }
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);
            // Cast the value to int if it's an integer field, otherwise string.
            field.set(null, field.getType().equals(Integer.TYPE) ? Integer.parseInt(value) : value);
            field.setAccessible(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setCertifiedPropsForGms() {
        if (sCertifiedProps.size() == 0) {
            Log.d(TAG, "Certified props are not set");
            return;
        }

        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    Log.d(TAG, "GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        if (!was) {
            Log.d(TAG, "Spoofing build for GMS");
            setCertifiedProps();
        } else {
            Log.d(TAG, "Skip spoofing build for GMS, because GmsAddAccountActivityOnTop");
        }
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static void setCertifiedProps() {
        for (String entry : sCertifiedProps) {
            // Each entry must be of the format FIELD:value
            final String[] fieldAndProp = entry.split(":", 2);
            if (fieldAndProp.length != 2) {
                Log.e(TAG, "Invalid entry in certified props: " + entry);
                continue;
            }
            setPropValue(fieldAndProp[0], fieldAndProp[1]);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            Log.d(TAG, "shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && sPixelFeatures.stream().anyMatch(name::contains)) {
                Log.d(TAG, "Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && name.equalsIgnoreCase(FEATURE_NEXUS_PRELOAD)) {
                Log.d(TAG, "Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        return has;
    }
}
