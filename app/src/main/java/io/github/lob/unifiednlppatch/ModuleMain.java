package io.github.lob.unifiednlppatch;

import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.github.libxposed.api.XposedModule;

public class ModuleMain extends XposedModule {
    static final String TAG = "ModuleMain";

    @Override
    @RequiresApi(Build.VERSION_CODES.Q)
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        // Module is universal
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
                                outValue.string = "org.microg.nlp";
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
                } catch (Throwable ignored) {
                	// this need to be empty to avoid error
                }
                return chain.proceed();
            });

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Error injecting resources on Android 15", t);
        }
    }
}
