plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.hilt) }

android { namespace = "com.nexa.feature.onboarding"; compileSdk = 35; defaultConfig { minSdk = 26 } }

dependencies { implementation(project(":core:designsystem")); implementation(libs.hilt.android); ksp(libs.hilt.compiler) }
