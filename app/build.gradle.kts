import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.gradle.proguard)
}

android {
    namespace = "pjs.dogbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "pjs.dogbox"
        minSdk = 28
        targetSdk = 37
        versionCode = 26080306
        versionName = "0.2.0"

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

proguardDictionaries {
    dictionaryNames = listOf(
        "build/class-dictionary.txt",
        "build/package-dictionary.txt",
        "build/obfuscation-dictionary.txt"
    )
    minLineLength = 3
    maxLineLength = 11
    linesCountInDictionary = 100000
}

afterEvaluate {
    tasks.matching {
        it.name.startsWith("minify") && it.name.endsWith("WithR8")
    }.configureEach {
        dependsOn("generateProguardDictionaries")
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
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.json)

    implementation(libs.squareup.okhttp)

    implementation(libs.coil3.coil)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.okhttp)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.dash)
    implementation(libs.androidx.media3.rtmp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.compose)
    implementation(libs.androidx.media3.okhttp)
    implementation(libs.androidx.media3.transformer)

    implementation(libs.materialKolor)
    implementation(libs.fleeksoft.ksoup)
    implementation(libs.com.tyme)

}