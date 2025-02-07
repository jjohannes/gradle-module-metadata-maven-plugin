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

package de.jjohannes.maven.gmm;

import com.google.gson.stream.JsonWriter;
import de.jjohannes.maven.gmm.checksums.HashUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Gradle module metadata file generator is responsible for generating a JSON file describing module metadata.
 */
public class GradleModuleMetadataWriter {

    private static final String FORMAT_VERSION = "1.1";

    private enum Variant {
        API_ELEMENTS("apiElements", "java-api", Collections.singletonList("compile")),
        RUNTIME_ELEMENTS("runtimeElements", "java-runtime", Arrays.asList("compile", "runtime"));

        private final String name;
        private final String usage;
        private final List<String> scopes;

        Variant(String name, String usage, List<String> scopes) {
            this.name = name;
            this.usage = usage;
            this.scopes = scopes;
        }
    }

    public static void generateTo(MavenProject project, String mavenVersion,
                                  List<Dependency> platformDependencies, List<Capability> capabilities,
                                  List<Dependency> removedDependencies,
                                  Writer writer) throws IOException {
        JsonWriter jsonWriter = new JsonWriter(writer);
        jsonWriter.setHtmlSafe(false);
        jsonWriter.setIndent("  ");
        writeComponentWithVariants(project, mavenVersion, platformDependencies, capabilities, removedDependencies, jsonWriter);
        jsonWriter.flush();
        writer.append('\n');
    }

    private static boolean isSnapshot(MavenProject project) {
        return project.getVersion().endsWith("SNAPSHOT");
    }

    private static Map<String, String> componentAttributes(MavenProject project) {
        return Collections.singletonMap("org.gradle.status", isSnapshot(project) ? "integration" : "release");
    }

    private static Map<String, String> variantAttributes(Variant variant) {
        Map<String, String> attributes = new TreeMap<>();

        attributes.put("org.gradle.category", "library");
        attributes.put("org.gradle.dependency.bundling", "external");
        attributes.put("org.gradle.libraryelements", "jar");

        attributes.put("org.gradle.usage", variant.usage);

        // attributes.put("org.gradle.jvm.version", ...);

        return attributes;
    }

    private static void writeComponentWithVariants(MavenProject project, String mavenVersion,
                                                   List<Dependency> platformDependencies, List<Capability> capabilities,
                                                   List<Dependency> removedDependencies,
                                                   JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        writeFormat(jsonWriter);
        writeIdentity(project, jsonWriter);
        writeCreator(mavenVersion, jsonWriter);
        writeVariants(project, platformDependencies, capabilities, removedDependencies, jsonWriter);
        jsonWriter.endObject();
    }

    private static void writeIdentity(MavenProject project, JsonWriter jsonWriter) throws IOException {
        Map<String, String> componentAttributes = componentAttributes(project);

        jsonWriter.name("component");
        jsonWriter.beginObject();
        jsonWriter.name("group");
        jsonWriter.value(project.getGroupId());
        jsonWriter.name("module");
        jsonWriter.value(project.getArtifactId());
        jsonWriter.name("version");
        jsonWriter.value(project.getVersion());
        writeAttributes(componentAttributes, jsonWriter);
        jsonWriter.endObject();
    }


    private static void writeVariants(MavenProject project,
                                      List<Dependency> platformDependencies, List<Capability> capabilities,
                                      List<Dependency> removedDependencies,
                                      JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("variants");
        jsonWriter.beginArray();
        writeVariant(project, Variant.API_ELEMENTS, platformDependencies, capabilities, removedDependencies, jsonWriter);
        writeVariant(project, Variant.RUNTIME_ELEMENTS, platformDependencies, capabilities, removedDependencies, jsonWriter);
        jsonWriter.endArray();
    }

    private static void writeCreator(String mavenVersion, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("createdBy");
        jsonWriter.beginObject();
        jsonWriter.name("maven");
        jsonWriter.beginObject();
        jsonWriter.name("version");
        jsonWriter.value(mavenVersion);
        jsonWriter.endObject();
        jsonWriter.endObject();
    }

    private static void writeFormat(JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("formatVersion");
        jsonWriter.value(FORMAT_VERSION);
    }

    private static void writeVariant(MavenProject project, Variant variant,
                                     List<Dependency> platformDependencies, List<Capability> capabilities,
                                     List<Dependency> removedDependencies,
                                     JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(variant.name);
        writeAttributes(variantAttributes(variant), jsonWriter);
        writeDependencies(variant, project.getDependencies(), platformDependencies, removedDependencies, jsonWriter);
        writeArtifacts(project, jsonWriter);
        writeCapabilities(project, capabilities, jsonWriter);

        jsonWriter.endObject();
    }

    private static void writeAttributes(Map<String, String> attributes, JsonWriter jsonWriter) throws IOException {
        if (attributes.isEmpty()) {
            return;
        }
        jsonWriter.name("attributes");
        jsonWriter.beginObject();

        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            jsonWriter.name(attribute.getKey());
            jsonWriter.value(attribute.getValue());
        }

        jsonWriter.endObject();
    }

    private static void writeArtifacts(MavenProject project, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("files");
        jsonWriter.beginArray();
        writeArtifact(project.getArtifact(), jsonWriter);
        jsonWriter.endArray();
    }

    private static void writeArtifact(Artifact artifact, JsonWriter jsonWriter) throws IOException {
        File file = artifact.getFile();
        String fileName = getFileNameForArtifact(artifact);

        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(fileName);
        jsonWriter.name("url");
        jsonWriter.value(fileName);

        jsonWriter.name("size");
        jsonWriter.value(file.length());
        writeChecksums(file, jsonWriter);

        jsonWriter.endObject();
    }

    private static String getFileNameForArtifact(Artifact artifact) {
        String originalFileName = artifact.getFile().getName();
        int fileExtensionIndex = originalFileName.lastIndexOf(".");
        if (fileExtensionIndex == -1) {
            return originalFileName;
        }
        String extension = originalFileName.substring(originalFileName.lastIndexOf(".") + 1);

        StringBuilder fileName = new StringBuilder();
        fileName.append(artifact.getArtifactId()).append('-');
        fileName.append(artifact.getVersion());
        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            fileName.append('-').append(artifact.getClassifier());
        }
        fileName.append('.').append(extension);
        return fileName.toString();
    }

    private static void writeChecksums(File artifact, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("sha512");
        jsonWriter.value(HashUtil.sha512(artifact).asHexString());
        jsonWriter.name("sha256");
        jsonWriter.value(HashUtil.sha256(artifact).asHexString());
        jsonWriter.name("sha1");
        jsonWriter.value(HashUtil.sha1(artifact).asHexString());
        jsonWriter.name("md5");
        jsonWriter.value(HashUtil.createHash(artifact, "md5").asHexString());
    }

    private static void writeDependencies(Variant variant,
                                          List<Dependency> dependencies, List<Dependency> platformDependencies,
                                          List<Dependency> removedDependencies,
                                          JsonWriter jsonWriter) throws IOException {
        if (dependencies.isEmpty() && isNullOrEmpty(platformDependencies)) {
            return;
        }
        jsonWriter.name("dependencies");
        jsonWriter.beginArray();
        for (Dependency dependency : dependencies) {
            if (Boolean.parseBoolean(dependency.getOptional())) {
                // Dependency is optional, all tooling ignores it
                continue;
            }
            if (!variant.scopes.contains(dependency.getScope())) {
                // Dependency is not in scope
                continue;
            }
            if (removedDependencies != null && removedDependencies.stream().anyMatch(removed ->
                    dependency.getGroupId().equals(removed.getGroupId()) && dependency.getArtifactId().equals(removed.getArtifactId()))) {
                // Dependency is explicitly removed (e.g. because the shade plugin removes it from the POM as well)
                continue;
            }
            writeDependency(dependency, false, jsonWriter);
        }
        if (!isNullOrEmpty(platformDependencies)) {
            for (Dependency dependency : platformDependencies) {
                if (dependency.getScope() == null || variant.scopes.contains(dependency.getScope())) {
                    writeDependency(dependency, true, jsonWriter);
                }
            }
        }
        jsonWriter.endArray();
    }

    private static void writeDependency(Dependency dependency, boolean toPlatform,
                                        JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("group");
        jsonWriter.value(dependency.getGroupId());
        jsonWriter.name("module");
        jsonWriter.value(dependency.getArtifactId());
        writeVersionConstraint(dependency.getVersion(), jsonWriter);
        writeExcludes(dependency.getExclusions(), jsonWriter);
        if (toPlatform) {
            writeAttributes(Collections.singletonMap("org.gradle.category", "platform"), jsonWriter);
            jsonWriter.name("endorseStrictVersions");
            jsonWriter.value(true);
        }
        if (!isNullOrEmpty(dependency.getClassifier()) || !"jar".equals(dependency.getType())) {
            writeDependencyArtifact(dependency, jsonWriter);
        }
        jsonWriter.endObject();
    }

    private static void writeVersionConstraint(String version, JsonWriter jsonWriter) throws IOException {
        if (version == null) {
            return;
        }
        jsonWriter.name("version");
        jsonWriter.beginObject();
        jsonWriter.name("requires");
        jsonWriter.value(version);
        jsonWriter.endObject();
    }

    private static void writeDependencyArtifact(Dependency dependency, JsonWriter jsonWriter) throws IOException {
        jsonWriter.name("thirdPartyCompatibility");
        jsonWriter.beginObject();

        jsonWriter.name("artifactSelector");
        jsonWriter.beginObject();
        jsonWriter.name("name");
        jsonWriter.value(dependency.getArtifactId());
        jsonWriter.name("type");
        jsonWriter.value(isNullOrEmpty(dependency.getType()) ? "jar" : dependency.getType());
        if (!isNullOrEmpty(dependency.getClassifier())) {
            jsonWriter.name("classifier");
            jsonWriter.value(dependency.getClassifier());
        }
        jsonWriter.endObject();

        jsonWriter.endObject();
    }
    private static void writeExcludes(List<Exclusion> excludes, JsonWriter jsonWriter) throws IOException {
        if (excludes.isEmpty()) {
            return;
        }
        jsonWriter.name("excludes");
        jsonWriter.beginArray();
        for (Exclusion exclude : excludes) {
            jsonWriter.beginObject();
            jsonWriter.name("group");
            jsonWriter.value(exclude.getGroupId());
            jsonWriter.name("module");
            jsonWriter.value(exclude.getArtifactId());
            jsonWriter.endObject();
        }
        jsonWriter.endArray();
    }

    private static void writeCapabilities(MavenProject project, List<Capability> capabilities,
                                          JsonWriter jsonWriter) throws IOException {
        if (capabilities != null && !capabilities.isEmpty()) {
            jsonWriter.name("capabilities");
            jsonWriter.beginArray();

            // default capability
            jsonWriter.beginObject();
            jsonWriter.name("group").value(project.getGroupId());
            jsonWriter.name("name").value(project.getArtifactId());
            jsonWriter.name("version").value(project.getVersion());
            jsonWriter.endObject();

            for (Capability capability : capabilities) {
                jsonWriter.beginObject();
                jsonWriter.name("group").value(capability.getGroupId());
                jsonWriter.name("name").value(capability.getArtifactId());
                if (isNullOrEmpty(capability.getVersion())) {
                    jsonWriter.name("version").value(project.getVersion());
                } else {
                    jsonWriter.name("version").value(capability.getVersion());
                }
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
        }
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static boolean isNullOrEmpty(List<?> l) {
        return l == null || l.isEmpty();
    }
}
