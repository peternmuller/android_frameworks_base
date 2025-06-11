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

import android.app.ActivityThread;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";

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

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();

        if (TextUtils.isEmpty(packageName)) {
            Log.e(TAG, "Null package");
            return;
        }

        if (packageName.equals(PACKAGE_GPHOTOS)) {
            Log.d(TAG, "Spoofing Pixel 1 for Google Photos");
            sPixelOneProps.forEach((PropImitationHooks::setPropValue));
        }
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

    public static boolean hasSystemFeature(String name, boolean has) {
        String packageName = ActivityThread.currentPackageName();
        
        if (packageName != null && packageName.contains(PACKAGE_GPHOTOS)) {
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
