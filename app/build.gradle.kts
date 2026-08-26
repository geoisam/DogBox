import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pjs.dogbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "pjs.dogbox"
        minSdk = 29
        targetSdk = 37
        versionCode = 26082810
        versionName = "0.3.3"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

    }

    signingConfigs {
        create("release") {
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            /*
            optimization {
                enable = false
            }
            */
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { output ->
                if (output is VariantOutputImpl) {
                    val appName = rootProject.name
                    val verName = output.versionName.orElse("unknown").get()
                    val verCode = output.versionCode.orElse(-1).get()

                    output.outputFileName.set("${appName}_v${verName}-${verCode}.apk")
                }
            }
        }
    }
}


dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.network)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.network)
    implementation(libs.ktor.server.negotiation)
    implementation(libs.ktor.kotlinx.json)

    implementation(libs.coil3.coil)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.dash)
    implementation(libs.androidx.media3.rtmp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.compose)
    implementation(libs.androidx.media3.transformer)

    implementation(libs.materialKolor)
    implementation(libs.fleeksoft.ksoup)
    implementation(libs.com.tyme)

}