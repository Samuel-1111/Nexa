plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }

android { namespace = "com.nexa.core.voice"; compileSdk = 35; defaultConfig { minSdk = 26 } }

dependencies { implementation(libs.androidx.core.ktx) }
