plugins {
    groovy
}

repositories {
    mavenCentral()
}

@Suppress("UnstableApiUsage")
testing.suites.named<JvmTestSuite>("test") {
    useJUnitJupiter()
    dependencies {
        implementation("com.google.code.gson:gson:2.12.1")
        implementation(gradleTestKit())
        implementation("org.spockframework:spock-core:2.3-groovy-3.0")
    }
}

