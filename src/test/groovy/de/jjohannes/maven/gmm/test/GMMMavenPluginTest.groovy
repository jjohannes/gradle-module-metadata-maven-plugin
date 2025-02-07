/*
 * Copyright the original author or authors.
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

package de.jjohannes.maven.gmm.test

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class GMMMavenPluginTest extends Specification {

    @TempDir
    File testFolder

    File mavenProducerBuild
    File gradleConsumerBuild

    def setupSpec() {
        installPluginLocally()
    }

    def setup() {
        def mavenProducer = new File(testFolder, "mavenProducer")
        def gradleConsumer = new File(testFolder, "gradleConsumer")
        mavenProducer.mkdirs()
        gradleConsumer.mkdirs()

        new File(gradleConsumer,'settings.gradle.kts') << 'rootProject.name = "consumer"'

        mavenProducerBuild = new File(mavenProducer, 'pom.xml')
        gradleConsumerBuild = new File(gradleConsumer, 'build.gradle')
    }

    def producerGMMPluginConfiguration(String pluginConfiguration, String packaging = "jar") {
        mavenProducerBuild << """
            <project>
              <!-- do_not_remove: published-with-gradle-metadata -->
              <modelVersion>4.0.0</modelVersion>
              <groupId>de.jjohannes</groupId>
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
                    <groupId>de.jjohannes</groupId>
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
        """
        installProducerLocally()
    }

    def consumerDependencies(String dependencyDeclarations) {
        gradleConsumerBuild << """
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
        """
    }

    def "capabilities are available"() {
        when:
        producerGMMPluginConfiguration """
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
        """

        consumerDependencies """
            implementation("de.jjohannes:gradle-module-metadata-maven-plugin-integration-test:1.0") {
                capabilities {
                    requireCapability("org.foo:another")
                }
            }
        """

        then:
        moduleJsonGenerated()
        resolve() == ['gradle-module-metadata-maven-plugin-integration-test-1.0.jar', 'commons-io-2.6.jar']
    }

    def "platform dependencies are available"() {
        when:
        producerGMMPluginConfiguration """
            <configuration>
              <platformDependencies>
                <dependency>
                  <groupId>com.fasterxml.jackson</groupId>
                  <artifactId>jackson-bom</artifactId>
                  <version>2.10.2</version>
                </dependency>
              </platformDependencies>
            </configuration>
        """

        consumerDependencies """
            implementation("de.jjohannes:gradle-module-metadata-maven-plugin-integration-test:1.0")
            implementation("com.fasterxml.jackson.core:jackson-core")
        """

        then:
        moduleJsonGenerated()
        resolve() == ['gradle-module-metadata-maven-plugin-integration-test-1.0.jar',
                      'jackson-core-2.10.2.jar',
                      'commons-io-2.6.jar']
    }

    def "does not generate GMM for BOMs"() {
        when:
        producerGMMPluginConfiguration "", "pom"

        then:
        !moduleJsonGenerated()
    }

    def testCapabilities() {
        expect:
        assertExpectedGMM("capabilities")
    }

    def testCombinedFeatures() {
        expect:
        assertExpectedGMM("combined-features")
    }

    def testPlatformDependencies() {
        expect:
        assertExpectedGMM("platform-dependencies")
    }

    def testSnapshotStatusAttribute() {
        expect:
        assertExpectedGMM("snapshot-status-attribute")
    }

    def testVariantDependencies() {
        expect:
        assertExpectedGMM("variant-dependencies")
    }

    def testParentDependencies() {
        expect:
        assertExpectedGMM("parent-dependencies")
    }

    def testCombineWithShadePlugin() {
        expect:
        assertExpectedGMM("combine-with-shade-plugin")
    }

    List<String> resolve() {
        def buildResult = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(gradleConsumerBuild.getParentFile())
                .withArguments('resolve', '-q').build()
        return buildResult.output.trim().split('\n')
    }

    void installPluginLocally() {
        print "mvn clean install -DskipTests -Dgpg.skip".execute(null, new File("..")).text
    }

    void installProducerLocally() {
        print "mvn clean install".execute(null, mavenProducerBuild.getParentFile()).text
    }

    void packageProducer() {
        print "mvn clean package".execute(null, mavenProducerBuild.getParentFile()).text
    }

    private boolean moduleJsonGenerated() {
        new File(mavenProducerBuild.parentFile, "target/publications/maven/module.json").exists()
    }

    private void assertExpectedGMM(String name) throws Exception  {
        File testPom = new File("src/test/resources/$name/pom.xml")
        File testPomParent = new File("src/test/resources/$name/parent/pom.xml")
        File gmmExpected = new File("src/test/resources/$name/expected-module.json")
        assertTrue(gmmExpected.exists())

        Files.copy(testPom.toPath(), mavenProducerBuild.toPath())
        if (testPomParent.exists()) {
            def mavenProducerParent = new File(mavenProducerBuild.parentFile, "parent/pom.xml")
            mavenProducerParent.parentFile.mkdirs()
            Files.copy(testPomParent.toPath(), mavenProducerParent.toPath())
        }

        packageProducer()

        File gmmActual = new File(mavenProducerBuild.parentFile, "target/publications/maven/module.json")
        assertTrue(gmmActual.exists())

        JsonElement expected = JsonParser.parseReader(new FileReader(gmmExpected))
        JsonElement actual = JsonParser.parseReader(new FileReader(gmmActual))
        assertEquals(expected, actual)
    }
}
