plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nexa.app"
    compileSdk = 35
    defaultConfig { applicationId = "com.nexa.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:notifications"))
    implementation(project(":core:voice"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:organizer"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:today"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation("androidx.hilt:hilt-work:1.3.0")
    implementation(libs.hilt.navigation.compose)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.hilt.compiler)
    ksp("androidx.hilt:hilt-compiler:1.3.0")
}
