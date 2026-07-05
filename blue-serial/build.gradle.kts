plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.chayanforyou.blueserial"
    compileSdk = 37

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// ------ Maven Publish Plugin ------ //

group = "io.github.chayanforyou"
version = "1.0.0"

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = name.toString(),
        version = version.toString()
    )

    pom {
        name = "Android Blue Serial"
        description = "A Android library designed to simplify serial communication over Bluetooth."
        url = "https://github.com/chayanforyou/Android-Blue-Serial"
        licenses {
            license {
                name = "MIT License"
                url = "http://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "chayanforyou"
                name = "Chayan Mistry"
                email = "chayanmistrry@gmail.com"
                url = "https://github.com/chayanforyou"
            }
        }
        scm {
            url = "https://github.com/chayanforyou/Android-Blue-Serial"
            connection = "scm:git:git://github.com/chayanforyou/Android-Blue-Serial.git"
            developerConnection = "scm:git:ssh://git@github.com/chayanforyou/Android-Blue-Serial.git"
        }
    }
}

// Fix Gradle warning about signing tasks using publishing
// task outputs without explicit dependencies:
// https://github.com/gradle/gradle/issues/26091
tasks.withType<PublishToMavenRepository> {
    dependsOn(tasks.withType<Sign>())
}