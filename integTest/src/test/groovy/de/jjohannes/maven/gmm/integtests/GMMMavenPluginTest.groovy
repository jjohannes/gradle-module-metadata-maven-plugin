/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jjohannes.maven.gmm.integtests


import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder;
import spock.lang.Specification

class GMMMavenPluginTest extends Specification {

    @Rule
    TemporaryFolder testFolder = new TemporaryFolder()

    File mavenProducerBuild
    File gradleConsumerBuild

    def setupSpec() {
        installPluginLocally()
    }

    def setup() {
        def mavenProducer = testFolder.newFolder("mavenProducer")
        def gradleConsumer = testFolder.newFolder("gradleConsumer")

        new File(gradleConsumer,'settings.gradle.kts') << 'rootProject.name = "consumer"'

        mavenProducerBuild = new File(mavenProducer, 'pom.xml')
        gradleConsumerBuild = new File(gradleConsumer, 'build.gradle')
    }

    def producerGMMPluginConfiguration(String pluginConfiguration) {
        mavenProducerBuild << """
            <project>
              <!-- do_not_remove: published-with-gradle-metadata -->
              <modelVersion>4.0.0</modelVersion>
              <groupId>de.jjohannes</groupId>
              <artifactId>gradle-module-metadata-maven-plugin-integration-test</artifactId>
              <version>1.0</version>
              <packaging>jar</packaging>
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
        resolve() == ['gradle-module-metadata-maven-plugin-integration-test-1.0.jar',
                      'jackson-core-2.10.2.jar',
                      'commons-io-2.6.jar']
    }

    List<String> resolve() {
        def buildResult = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(gradleConsumerBuild.getParentFile())
                .withArguments('resolve', '-q').build()
        return buildResult.output.trim().split('\n')
    }


    void installPluginLocally() {
        print "mvn clean install -DskipTests".execute(null, new File("..")).text
    }

    void installProducerLocally() {
        print "mvn clean install".execute(null, mavenProducerBuild.getParentFile()).text
    }
}
