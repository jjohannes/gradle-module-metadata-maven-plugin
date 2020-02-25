plugins {
    groovy
}

repositories {
    jcenter()
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
}
