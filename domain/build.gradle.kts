plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    implementation(project(":core:common"))
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
