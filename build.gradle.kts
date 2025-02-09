plugins {
    id("org.gradlex.maven-plugin-development") version "1.0.2"
    id("com.gradleup.nmcp") version "0.0.9"
    id("maven-publish")
    id("signing")
    id("checkstyle")
}

group = "org.gradlex"
version = "1.0"

val mvnVersion = "3.9.9"

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")

    compileOnly("org.apache.maven:maven-core:$mvnVersion")
    compileOnly("org.apache.maven:maven-plugin-api:$mvnVersion")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.15.1")
}

mavenPlugin {
    name = "Gradle Module Metadata Maven Plugin"
    description = "A Maven plugin to publish Gradle Module Metadata"
    helpMojoPackage = "org.gradlex.maven.gmm"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    withSourcesJar()
    withJavadocJar()
}

tasks.compileJava {
    options.release = 8
    options.compilerArgs.add("-Werror")
}

tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        addStringOption("Xdoclint:all,-missing", "-Xwerror")
        tags(
            "goal:a:Goal:",
            "requiresProject:a:Requires Project:",
            "threadSafe:a:Thread Safe:"
        )
    }
}

@Suppress("UnstableApiUsage")
testing.suites.named<JvmTestSuite>("test") {
    useJUnitJupiter()
    dependencies {
        implementation(gradleTestKit())
    }
}

publishing {
    publications.create<MavenPublication>("mavenPlugin") {
        from(components["java"])
        pom {
            name = mavenPlugin.name
            description = mavenPlugin.description
            url = "https://github.com/gradlex-org/gradle-module-metadata-maven-plugin"
            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            scm {
                connection = "scm:git:git://github.com/gradlex-org/gradle-module-metadata-maven-plugin.git"
                developerConnection = "scm:git:git://github.com/gradlex-org/gradle-module-metadata-maven-plugin.git"
                url = "https://github.com/gradlex-org/gradle-module-metadata-maven-plugin"
            }
            developers {
                developer {
                    name = "Jendrik Johannes"
                    email = "jendrik@gradlex.org"
                }
            }
        }
    }
}

signing {
    if (providers.gradleProperty("sign").getOrElse("false").toBoolean()) {
        useInMemoryPgpKeys(
            providers.environmentVariable("SIGNING_KEY").getOrNull(),
            providers.environmentVariable("SIGNING_PASSPHRASE").getOrNull()
        )
        sign(publishing.publications["mavenPlugin"])
    }
}

nmcp {
    publish("mavenPlugin") {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME")
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD")
        publicationType = "USER_MANAGED" // "AUTOMATIC"
    }
}

checkstyle {
    configDirectory = layout.projectDirectory.dir("gradle/checkstyle")
}

tasks.checkstyleMain {
    exclude("**/HelpMojo.java")
}
