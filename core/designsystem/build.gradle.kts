plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }

android { namespace = "com.nexa.core.designsystem"; compileSdk = 35; defaultConfig { minSdk = 26 }; buildFeatures { compose = true } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
