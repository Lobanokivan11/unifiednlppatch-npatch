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
import io.github.libxposed.api.XposedInterface.HookHandle;
import java.util.ArrayList;
import java.util.List;

public class ModuleMain extends XposedModule {
    static final String TAG = "Nlp";
    static final String TARGET_PKG = "org.microg.nlp";
    private final List<HookHandle> currentHooks = new ArrayList<>();

    @Override
    @RequiresApi(Build.VERSION_CODES.Q)
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        // universal
    }

    @Override
    @SuppressLint("SoonBlockedPrivateApi")
    public void onPackageReady(@NonNull PackageReadyParam param) {
        // clear hooks for hot reloading support
        currentHooks.clear();

        // 1. Hook the System Resources
        hookResources(param.getClassLoader());

        // 2. Hook Location Provider Packages (CRITICAL: Added this missing call)
        hookSettingsSecure(param.getClassLoader());

        // 3. Hook LocationManager Package Names
        hookLocationManagerProviders(param.getClassLoader());
    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        log(Log.INFO, TAG, "onHotReloading");
        param.setSavedInstanceState("Hello from last generation");
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        log(Log.INFO, TAG, "onHotReloaded: " + param.getProcessName() + ", " + param.getOldHookHandles().size() + " old hooks");
        log(Log.INFO, TAG, "savedInstanceState: " + param.getSavedInstanceState());
        
        // correctly unsubscribe from old hooks
        param.getOldHookHandles().forEach(HookHandle::unhook);
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private void hookResources(ClassLoader classLoader) {
        try {
            Class<?> resourcesImplClass = Class.forName("android.content.res.ResourcesImpl", true, classLoader);
            var getValueMethod = resourcesImplClass.getDeclaredMethod("getValue", int.class, TypedValue.class, boolean.class);
            
            HookHandle handle = hook(getValueMethod).intercept(chain -> {
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
                                outValue.data = 0; // Disable overlays to force using the explicit package name
                                outValue.changingConfigurations = 0;
                                return null;
                        }
                    }
                } catch (Throwable ignored) {}
                return chain.proceed();
            });
            currentHooks.add(handle);

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Error injecting resources", t);
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    public void hookSettingsSecure(ClassLoader classLoader) {
        try {
            Class<?> settingsSecureClass = Class.forName("android.provider.Settings$Secure", true, classLoader);
            var getStringMethod = settingsSecureClass.getDeclaredMethod("getString", ContentResolver.class, String.class);

            HookHandle handle = hook(getStringMethod).intercept(chain -> {
                String name = (String) chain.getArg(1);
                // Hooking settings strings evaluated by microG self-check tools
                if ("location_provider_allowed_packages".equals(name) || "location_network_provider_package".equals(name)) {
                    return TARGET_PKG;
                }
                return chain.proceed();
            });
            currentHooks.add(handle);
        } catch (Throwable t) {
            log(Log.DEBUG, TAG, "Settings.Secure hook skipped or failed", t);
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    public void hookLocationManagerProviders(ClassLoader classLoader) {
        try {
            Class<?> locationManagerClass = Class.forName("android.location.LocationManager", true, classLoader);
            
            try {
                var getProviderPackageMethod = locationManagerClass.getDeclaredMethod("getProviderPackage", String.class);
                HookHandle handle = hook(getProviderPackageMethod).intercept(chain -> {
                    String provider = (String) chain.getArg(0);
                    if (LocationManager.NETWORK_PROVIDER.equals(provider) || "fused".equals(provider)) {
                        return TARGET_PKG;
                    }
                    return chain.proceed();
                });
                currentHooks.add(handle);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            log(Log.DEBUG, TAG, "LocationManager hook skipped or failed", t);
        }
    }
}
