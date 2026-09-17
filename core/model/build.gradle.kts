plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    implementation(project(":core:common"))
    testImplementation(libs.junit)
}
