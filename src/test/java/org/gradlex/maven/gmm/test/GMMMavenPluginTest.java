/*
 * Copyright the GradleX team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradlex.maven.gmm.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;

class GMMMavenPluginTest {

    @TempDir
    File testFolder;

    File mavenProducerBuild;
    File gradleConsumerBuild;

    @BeforeAll
    static void setupSpec() {
        installPluginLocally();
    }

    @BeforeEach
    void setup() throws IOException {
        File mavenProducer = new File(testFolder, "mavenProducer");
        File gradleConsumer = new File(testFolder, "gradleConsumer");
        createDirectories(mavenProducer.toPath());
        createDirectories(gradleConsumer.toPath());
        writeString(new File(gradleConsumer, "settings.gradle.kts").toPath(), "rootProject.name = \"consumer\"");

        mavenProducerBuild = new File(mavenProducer, "pom.xml");
        gradleConsumerBuild = new File(gradleConsumer, "build.gradle");
    }

    void producerGMMPluginConfiguration(String pluginConfiguration, String packaging) {
        try {
            writeString(mavenProducerBuild.toPath(), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.gradlex</groupId>
                  <artifactId>gradle-module-metadata-maven-plugin-integration-test</artifactId>
                  <version>1.0</version>
                  <packaging>$packaging</packaging>
                  <name>Test GMM</name>
    
                  <dependencies>
                    <dependency>
                      <groupId>commons-io</groupId>
                      <artifactId>commons-io</artifactId>
                      <version>2.6</version>
                    </dependency>
                  </dependencies>
    
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.gradlex</groupId>
                        <artifactId>gradle-module-metadata-maven-plugin</artifactId>
                        <executions>
                          <execution>
                            <goals>
                              <goal>gmm</goal>
                            </goals>
                          </execution>
                        </executions>
                        $pluginConfiguration
                      </plugin>
                    </plugins>
                  </build>
                </project>
            """.replace("$pluginConfiguration", pluginConfiguration).replace("$packaging", packaging));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        installProducerLocally();
    }

    void consumerDependencies(String dependencyDeclarations) {
        try {
            writeString(gradleConsumerBuild.toPath(), """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
                dependencies {
                    $dependencyDeclarations
                }
                tasks.register("resolve") {
                    doLast {
                        configurations.compileClasspath.files.forEach { println(it.name) }
                    }
                }
            """.replace("$dependencyDeclarations", dependencyDeclarations));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void marker_comment_is_added_to_pom() {
        producerGMMPluginConfiguration("", "jar");
        assertThat(mavenProducerBuild).content().contains(
                "<modelVersion>4.0.0</modelVersion> <!-- do_not_remove: published-with-gradle-metadata -->");
    }

    @Test
    void capabilities_are_available() {
        producerGMMPluginConfiguration("""
            <configuration>
              <capabilities>
                <capability>
                  <groupId>org.example</groupId>
                  <artifactId>other</artifactId>
                </capability>
                <capability>
                  <groupId>org.foo</groupId>
                  <artifactId>another</artifactId>
                  <version>0.1.2</version>
                </capability>
              </capabilities>
            </configuration>
        """, "jar");

        consumerDependencies("""
            implementation("org.gradlex:gradle-module-metadata-maven-plugin-integration-test:1.0") {
                capabilities {
                    requireCapability("org.foo:another")
                }
            }
        """);

        moduleJsonGenerated();
        assertThat(resolve()).containsExactly(
                "gradle-module-metadata-maven-plugin-integration-test-1.0.jar", "commons-io-2.6.jar");
    }

    @Test
    void platform_dependencies_are_available() {
        producerGMMPluginConfiguration("""
            <configuration>
              <platformDependencies>
                <dependency>
                  <groupId>com.fasterxml.jackson</groupId>
                  <artifactId>jackson-bom</artifactId>
                  <version>2.10.2</version>
                </dependency>
              </platformDependencies>
            </configuration>
        """, "jar");

        consumerDependencies("""
            implementation("org.gradlex:gradle-module-metadata-maven-plugin-integration-test:1.0")
            implementation("com.fasterxml.jackson.core:jackson-core")
        """);

        moduleJsonGenerated();
        assertThat(resolve()).containsExactly(
                "gradle-module-metadata-maven-plugin-integration-test-1.0.jar",
                "jackson-core-2.10.2.jar",
                "commons-io-2.6.jar");
    }

    @Test
    void does_not_generate_GMM_for_BOMs() {
        producerGMMPluginConfiguration("", "pom");

        moduleJsonNotGenerated();
    }

    @Test
    void testCapabilities() {
        assertExpectedGMM("capabilities");
    }

    @Test
    void testCombineWithShadePlugin() {
        assertExpectedGMM("combine-with-shade-plugin");
    }

    @Test
    void testOptionalDependencies() {
        assertExpectedGMM("optional-dependencies");
    }

    @Test
    void testCombinedFeatures() {
        assertExpectedGMM("combined-features");
    }

    @Test
    void testCompileOnlyApiDependencies() {
        assertExpectedGMM("compile-only-api-dependencies");
    }

    @Test
    void testParentDependencies() {
        assertExpectedGMM("parent-dependencies");
    }

    @Test
    void testPlatformDependencies() {
        assertExpectedGMM("platform-dependencies");
    }

    @Test
    void testSnapshotStatusAttribute() {
        assertExpectedGMM("snapshot-status-attribute");
    }

    @Test
    void testVariantDependencies() {
        assertExpectedGMM("variant-dependencies");
    }

    List<String> resolve() {
        BuildResult buildResult = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(gradleConsumerBuild.getParentFile())
                .withArguments("resolve", "-q").build();
        return Arrays.asList(buildResult.getOutput().trim().split("\n"));
    }

    private static void installPluginLocally() {
        exec("./gradlew publishToMavenLocal", null);

    }

    private void installProducerLocally() {
        exec("mvn clean install -DskipTests -Dgpg.skip", mavenProducerBuild.getParentFile());
    }

    private void packageProducer() {
        exec("mvn clean package", mavenProducerBuild.getParentFile());
    }

    private void moduleJsonGenerated() {
        assertThat(new File(mavenProducerBuild.getParentFile(), "target/publications/maven/module.json")).exists();
    }

    private void moduleJsonNotGenerated() {
        assertThat(new File(mavenProducerBuild.getParentFile(), "target/publications/maven/module.json")).doesNotExist();
    }

    private void assertExpectedGMM(String name) {
        File testPom = new File("src/test/resources/" + name + "/pom.xml");
        File testPomParent = new File("src/test/resources/" + name + "/parent/pom.xml");
        File gmmExpected = new File("src/test/resources/" + name + "/expected-module.json");
        assertThat(gmmExpected).exists();

        try {
            Files.copy(testPom.toPath(), mavenProducerBuild.toPath());
            if (testPomParent.exists()) {
                File mavenProducerParent = new File(mavenProducerBuild.getParentFile(), "parent/pom.xml");
                Files.createDirectories(mavenProducerParent.getParentFile().toPath());
                Files.copy(testPomParent.toPath(), mavenProducerParent.toPath());
            }

            packageProducer();

            File gmmActual = new File(mavenProducerBuild.getParentFile(), "target/publications/maven/module.json");
            assertThat(gmmActual).exists();

            JsonElement expected = JsonParser.parseReader(new FileReader(gmmExpected));
            JsonElement actual = JsonParser.parseReader(new FileReader(gmmActual));
            assertThat(expected).isEqualTo(actual);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void exec(String command, File workDir) {
        try {
            Process proc = Runtime.getRuntime().exec(command, null, workDir);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
