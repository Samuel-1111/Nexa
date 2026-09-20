plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }

android { namespace = "com.nexa.core.notifications"; compileSdk = 35; defaultConfig { minSdk = 26 } }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
