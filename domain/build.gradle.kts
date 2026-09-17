plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    // api, not implementation: Task/Reminder/Note/Memory from core:model appear
    // in this module's own public interfaces (TaskRepository etc.), so every
    // consumer of :domain needs them on its compile classpath transitively.
    implementation(project(":core:common"))
    api(project(":core:model"))
    testImplementation(libs.junit)
}
