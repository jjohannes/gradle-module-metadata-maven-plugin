# Maven plugin to publish Gradle Module Metadata

[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fgradlex-org%2Fgradle-module-metadata-maven-plugin%2Fbadge%3Fref%3Dmain&style=flat)](https://actions-badge.atrox.dev/gradlex-org/gradle-module-metadata-maven-plugin/goto?ref=main)
[![Maven Central](https://img.shields.io/maven-metadata/v?label=Maven%Central&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Forg%2Fgradlex%2Fgradle-module-metadata-maven-plugin%2Fmaven-metadata.xml)](https://repo1.maven.org/maven2/org/gradlex/gradle-module-metadata-maven-plugin/maven-metadata.xml)

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
      <version>1.0</version>
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

Note: You may apply the plugin in a parent POM to publish Gradle Module Metadata for all modules using that parent.

## Adding platform dependencies

In Gradle terminology, a _platform_ is a [component to control transitive dependencies](https://docs.gradle.org/current/userguide/platforms.html).
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

## Adding a compileOnlyApi dependency

When a Java library is used in a project, there are two different contexts it is used in:
**Compile Classpath** and **Runtime Classpath**.
For each dependency, the scope determines it's transitive visibility. There are four possible combinations:

1. `api` transitively visible at **compile time** and **runtime** (in Maven: `compile`)
2. `implementation` / `runtimeOnly` transitively visible only at **runtime** (in Maven: `runtime`)
3. `compileOnlyApi` transitively visible only at **compile time** (no counterpart in Maven)
4. `compileOnly` not transitively visible at all (in Maven: `provided`)

The third case is interesting for annotation libraries that should be available to the Java compiler,
and it's annotation processing, transitively. As this is not supported by Maven, these dependencies are usually
omitted transitively (by defining them in the `provided` scope). With this plugin, `compileOnlyApi` dependencies
can be added for Gradle users as follows:

```
<compileOnlyApiDependencies>
  <dependency>
    <groupId>com.google.errorprone</groupId>
    <artifactId>error_prone_annotations</artifactId>
    <version>2.36.0</version>
  </dependency>
</compileOnlyApiDependencies>
```

## Defining capabilities

A [capability](https://docs.gradle.org/current/userguide/dependency_capability_conflict.html) is defined by GAV coordinates just as a published component.
This can be used to express that two different components implement the same thing (i.e. the same _capability_) and can thus not be used together.
If Gradle 6+ detects multiple components with the same capability in a dependency graph, it fails and allows the user to define the resolution (i.e. select one of the conflicting implementations).
An example, which is described in detail in this [blog post](https://blog.gradle.org/addressing-logging-complexity-capabilities), is that `org.slf4j:slf4j-simple` and `ch.qos.logback:logback-classic` are both SLF4J bindings and only one can be used at runtime (so one should be chosen).

Another use case is the relocation of a module to new coordinates.
Then the never versions can define the old GAV coordinates as capability to indicate that both implement the same thing.
(If a component does not define capabilities, like all components published only with POM metadata, they automatically have a single capability corresponding to the component's GAV coordinates.)
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

## Removing a dependency that is removed from the POM by another plugin

The plugin automatically adds dependencies defined in the original `pom.xml` to the Gradle Module Metadata.
In cases where a modified `pom.xml`, with a different set of dependencies, gets published, this may not be correct.
A prominent case is using the `maven-shade-plugin` with the `<createDependencyReducedPom>` option. In this case,
some dependencies are removed for publishing. The Gradle Metadata plugin should then be configured
to not add the corrsponding dependencies to the Gradle Metadata as well. This can be done as follows:

```
<removedDependencies>
  <dependency>
    <groupId>org.ow2.asm</groupId>
    <artifactId>asm</artifactId>
  </dependency>
</removedDependencies>
```

Note: If the `pom.xml` is rewritten by another plugin, it may also remove the marker comment
`<!-- do_not_remove: published-with-gradle-metadata -->`. It is essential that the comments is added back
to the published `pom.xml`. In the case of the shade plugin, this can be done using the
`find-and-replace-maven-plugin` on the generated `dependency-reduced-pom.xml`. You can find a full example
[here](src/test/resources/combine-with-shade-plugin/pom.xml).

## Supporting more features

Gradle Module Metadata offers many more features.
To make use of them, you should consider to use [Gradle 6+](https://docs.gradle.org/current/userguide/getting_started.html) directly as build tool for your library.

There might still be some other interesting things that could be done in Maven builds by extending this plugin.
In that case, please [file an issue](https://github.com/jjohannes/gradle-module-metadata-maven-plugin/issues) (or [open a pull request](https://github.com/jjohannes/gradle-module-metadata-maven-plugin/pulls)) that describes the use case.

# Disclaimer

Gradle and the Gradle logo are trademarks of Gradle, Inc.
The GradleX project is not endorsed by, affiliated with, or associated with Gradle or Gradle, Inc. in any way.
