import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexa.core.network"
    compileSdk = 35
    defaultConfig { minSdk = 26 }

    // NEXA_SUPABASE_URL / NEXA_SUPABASE_ANON_KEY may be overridden by local.properties
    // (gitignored) or CI secrets. The publishable client key is safe to ship in
    // an Android client and is protected by Supabase RLS. The service-role key
    // must never appear in this app.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("NEXA_SUPABASE_URL", "https://blunbsmuohzregwubdex.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("NEXA_SUPABASE_ANON_KEY", "sb_publishable_aDPGTtSWQaoyBK4I1cB0KA_CwcRDb8C")}\"")
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core:model"))
    // api: SupabaseClient/HttpClient types appear in this module's own public
    // constructors (AuthRepository, AiGatewayClient), so :app needs them too.
    api(platform(libs.supabase.bom))
    api("io.github.jan-tennert.supabase:postgrest-kt")
    api("io.github.jan-tennert.supabase:auth-kt")
    api("io.github.jan-tennert.supabase:functions-kt")
    api(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)
}
