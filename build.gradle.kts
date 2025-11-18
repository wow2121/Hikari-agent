// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("io.realm.kotlin") version "1.13.0" apply false
    // Firebase plugin commented out - will add when implementing cloud sync
    // id("com.google.gms.google-services") version "4.4.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
