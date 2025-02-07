plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0" // replace with id("com.gradleup.nmcp") version "0.0.9"
    id("org.gradlex.maven-plugin-development") version "1.0.2"
    id("maven-publish")
    id("signing")
    id("checkstyle")
}

group = "de.jjohannes"
version = "0.5.0"

val mvnVersion = "3.9.9"

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")

    compileOnly("org.apache.maven:maven-core:$mvnVersion")
    compileOnly("org.apache.maven:maven-plugin-api:$mvnVersion")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.15.1")
}

mavenPlugin {
    helpMojoPackage = "de.jjohannes.maven.gmm"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.compileJava {
    options.release = 8
    options.compilerArgs.add("-Werror")
}

tasks.javadoc {
    // Enable all JavaDoc checks, but the one requiring JavaDoc everywhere
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-Xwerror")
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
            name = "Gradle Module Metadata Maven Plugin"
            description = "A Maven plugin to publish Gradle Module Metadata"
            url = "https://github.com/jjohannes/gradle-module-metadata-maven-plugin"
            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            scm {
                connection = "scm:git:git://github.com/jjohannes/gradle-module-metadata-maven-plugin.git"
                developerConnection = "scm:git:git://github.com/jjohannes/gradle-module-metadata-maven-plugin.git"
                url = "https://github.com/jjohannes/gradle-module-metadata-maven-plugin"
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

// signing {
//     useInMemoryPgpKeys(
//         providers.environmentVariable("SIGNING_KEY").getOrNull(),
//         providers.environmentVariable("SIGNING_PASSPHRASE").getOrNull()
//     )
//     if (providers.environmentVariable("CI").getOrElse("false").toBoolean()) {
//         sign(publishing.publications["mavenPlugin"])
//     }
// }

nexusPublishing {
    repositories.sonatype {
        username = providers.environmentVariable("NEXUS_USERNAME")
        password = providers.environmentVariable("NEXUS_PASSWORD")
    }
}

checkstyle {
    configDirectory = layout.projectDirectory.dir("gradle/checkstyle")
}

tasks.checkstyleMain {
    exclude("**/HelpMojo.java")
}
