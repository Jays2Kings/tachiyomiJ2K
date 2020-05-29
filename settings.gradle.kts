pluginManagement {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
            else if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
            else if (requested.id.id.equals("com.google.gms.google-services")) {
                useModule("com.google.gms:google-services:${requested.version}")
            }
            else if (requested.id.id.equals("com.google.android.gms.oss-licenses-plugin")) {
                useModule("com.google.android.gms:oss-licenses-plugin:${requested.version}")
            }
        }
    }
}

include(":app")
