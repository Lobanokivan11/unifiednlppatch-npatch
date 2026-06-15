package io.github.lob.unifiednlppatch;

import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.provider.Settings;
import android.content.ContentResolver;
import android.location.LocationManager;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.github.libxposed.api.XposedModule;

public class ModuleMain extends XposedModule {
    static final String TAG = "Nlp";
    static final String TARGET_PKG = "org.microg.nlp";

    @Override
    @RequiresApi(Build.VERSION_CODES.Q)
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        hookLocationManagerProviders(param.classLoader);
    }

    @Override
    @SuppressLint("SoonBlockedPrivateApi")
    public void onPackageReady(@NonNull PackageReadyParam param) {
        try {
            Class<?> resourcesImplClass = Class.forName("android.content.res.ResourcesImpl", true, param.getClassLoader());
            var getValueMethod = resourcesImplClass.getDeclaredMethod("getValue", int.class, TypedValue.class, boolean.class);
            
            hook(getValueMethod).intercept(chain -> {
                int resId = (int) chain.getArg(0);
                TypedValue outValue = (TypedValue) chain.getArg(1);
                
                try {
                    String resName = "";
                    try {
                        var getResourceEntryName = resourcesImplClass.getDeclaredMethod("getResourceEntryName", int.class);
                        resName = (String) getInvoker(getResourceEntryName)
                                .setType(Invoker.Type.ORIGIN)
                                .invoke(chain.getThisObject(), resId);
                    } catch (Exception ignored) {}

                    if (resName != null) {
                        switch (resName) {
                            case "config_geocoderProviderPackageName":
                            case "config_networkLocationProviderPackageName":
                            case "config_osNetworkLocationProviderPackageName":
                            case "config_regionNetworkLocationProviderPackageName":
                            case "config_hardwareFlpPackageName":
                            case "config_fusedLocationProviderPackageName":
                            case "config_defaultNetworkRecommendationProviderPackage":
                                outValue.type = TypedValue.TYPE_STRING;
                                outValue.string = TARGET_PKG;
                                outValue.changingConfigurations = 0;
                                return null;
                            case "config_enableGeocoderOverlay":
                            case "config_enableNetworkLocationOverlay":
                            case "config_enableNetworkLocationProviderOverlay":
                                outValue.type = TypedValue.TYPE_INT_BOOLEAN;
                                outValue.data = 0;
                                outValue.changingConfigurations = 0;
                                return null;
                        }
                    }
                } catch (Throwable ignored) {}
                return chain.proceed();
            });

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Error injecting resources", t);
        }
        hookLocationManagerProviders(param.classLoader);
    }

    public void hookSettingsSecure(ClassLoader classLoader) {
        try {
            Class<?> settingsSecureClass = Class.forName("android.provider.Settings$Secure", true, classLoader);
            var getStringMethod = settingsSecureClass.getDeclaredMethod("getString", ContentResolver.class, String.class);

            hook(getStringMethod).intercept(chain -> {
                String name = (String) chain.getArg(1);
                if ("location_provider_allowed_packages".equals(name) || "location_network_provider_package".equals(name)) {
                    return TARGET_PKG;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.DEBUG, TAG, "Settings.Secure hook skipped or failed", t);
        }
    }

    public void hookLocationManagerProviders(ClassLoader classLoader) {
        try {
            Class<?> locationManagerClass = Class.forName("android.location.LocationManager", true, classLoader);
            
            try {
                var getProviderPackageMethod = locationManagerClass.getDeclaredMethod("getProviderPackage", String.class);
                hook(getProviderPackageMethod).intercept(chain -> {
                    String provider = (String) chain.getArg(0);
                    if (LocationManager.NETWORK_PROVIDER.equals(provider) || "fused".equals(provider)) {
                        return TARGET_PKG;
                    }
                    return chain.proceed();
                });
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            log(Log.DEBUG, TAG, "LocationManager hook skipped or failed", t);
        }
    }
}
