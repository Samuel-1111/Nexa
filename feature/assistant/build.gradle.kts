plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.hilt) }

android { namespace = "com.nexa.feature.assistant"; compileSdk = 35; defaultConfig { minSdk = 26 }; buildFeatures { compose = true } }

dependencies { implementation(project(":core:designsystem")); implementation(project(":core:voice")); implementation(project(":domain")); implementation(libs.androidx.lifecycle.viewmodel.compose); implementation(libs.hilt.android); ksp(libs.hilt.compiler) }
