plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }

android { namespace = "com.nexa.data"; compileSdk = 35; defaultConfig { minSdk = 26 } }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    api(platform(libs.supabase.bom))
    api("io.github.jan-tennert.supabase:postgrest-kt")
    api("io.github.jan-tennert.supabase:auth-kt")
    implementation(project(":core:notifications"))
    implementation(project(":domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.hilt:hilt-work:1.3.0")
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.hilt.compiler)
    ksp("androidx.hilt:hilt-compiler:1.3.0")
}
