# Maven plugin to publish Gradle Module Metadata

This Maven plugin is for JVM library developers that use Maven as build tool, but are also interested in supporting Gradle users of their library by leveraging some of the new dependency managment features introdued with Gradle 6.

This plugin allows you to publish [Gradle Module Metadata](https://github.com/gradle/gradle/blob/master/subprojects/docs/src/docs/design/gradle-module-metadata-latest-specification.md) for libraries built with Maven.
It automatically adds the same dependency information present in the POM to a Gradle Module Metadata file (`.module`) and adds that file as additional artifact to be _installed_ and _published_.
If Gradle Module Metadata is published, [Gradle 6+](https://gradle.org)  will use that information in place of the POM.

Because Gradle Module Metadata is a richer format, it allows you to add additional information about your library.
If published, Gradle 6+ can make use of that information during dependency resolution to detect and solve dependency conflicts that involve your library.
Currently, this plugin supports the following features that cannot be expressed in POM metadata:

- [Platform Dependencies](https://blog.gradle.org/alignment-with-gradle-module-metadata)
- [Capabilities](https://blog.gradle.org/addressing-logging-complexity-capabilities)

## Using the Plugin

Add this line to the top of you `pom.xml` right in the beginning of the `<project>` tag:

```
<!-- do_not_remove: published-with-gradle-metadata -->
```

Add the plugin to your build:

```
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
      <configuration>
        <platformDependencies>
          <dependency>
            <groupId>...</groupId>
            <artifactId>...</artifactId>
            <version>...</version>
          </dependency>
        </platformDependencies>
        <capabilities>
          <capability>
            <groupId>...</groupId>
            <artifactId>...</artifactId>
          </capability>
        </capabilities>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Adding platform dependencies

In Gradle terminology, a _platform_ is a [component to control transitive dependencies](https://docs.gradle.org/current/userguide/platforms.htm).
A Maven BOM can be used as a platform by declaring a dependency to it.
Which means that, in contrast to Maven, another component can depend on a BOM instead of importing it.
The advantage is that such a _platform dependency_ can be published in Gradle Module Metadata.
This can, for example, be used to automatically align versions of related components as illustrated in this [blog post](https://blog.gradle.org/alignment-with-gradle-module-metadata).

This plugin allows you to add platform dependencies _in addition_ to the dependencies defined in the POM.
You define a platform dependency inside the `<platformDependencies>` block in the plugin configuration.

For example:

```
<platformDependencies>
  <dependency>
    <groupId>com.fasterxml.jackson</groupId>
    <artifactId>jackson-bom</artifactId>
    <version>2.10.2</version>
  </dependency>
</platformDependencies>
```

If you add that to your POM, the `jackson-bom` will automatically be used by all Gradle 6+ builds depending on your library. 

## Defining capabilities

A [capability](https://docs.gradle.org/current/userguide/dependency_capability_conflict.html) is defined by GAV coordinates just as a published component.
This can be used to express that two different components implement the same thing (i.e. the same _capability_) and can thus not be used together.
If Gradle 6+ detects multiple components with the same capability in a dependency graph it fails and allows the user to define the resolution (i.e. select one of the conflicting implementations).
An example, which is described in detail in this [blog post](https://blog.gradle.org/addressing-logging-complexity-capabilities), is that `org.slf4j:slf4j-simple` and `ch.qos.logback:logback-classic` are both SLF4J bindings and only one can be used as runtime (so one should be chosen).

Another use case is the relocation of a module to new coordinates.
Then the never versions can define the old GAV coordinates as capability to indicate that both implement the same thing.
(If a component does not define capabilities, like all modules published only with POM metadata, they automatically have a single capability corresponding to the component's GAV coordinates.)
An example, from this [blog post](https://blog.gradle.org/guava), is `com.google.collections:google-collections` that was relocated to `com.google.guava:guava`

```
<capabilities>
  <capability>
    <groupId>com.google.collections</groupId>
    <artifactId>google-collections</artifactId>
    <!-- Optional: <version>...</version> --> 
  </capability>
</capabilities>
```

## Supporting more features

Gradle Module Metadata offers many more features.
To make use of them, you should consider to use [Gradle 6+](https://docs.gradle.org/current/userguide/getting_started.html) directly as build tool for your library.

There might still be some other interesting things that could be done in Maven builds by extending this plugin.
In that case, please [file an issue](https://github.com/jjohannes/gradle-module-metadata-maven-plugin/issues) (or [open a pull request](https://github.com/jjohannes/gradle-module-metadata-maven-plugin/pulls)) that describes the use case.