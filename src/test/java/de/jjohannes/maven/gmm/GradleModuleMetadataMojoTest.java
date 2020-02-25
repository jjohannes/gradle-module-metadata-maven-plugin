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
package de.jjohannes.maven.gmm;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.maven.plugin.testing.MojoRule;

import org.junit.Rule;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

public class GradleModuleMetadataMojoTest {

    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    public void testCapabilities() throws Exception {
        assertExpectedGMM("capabilities");
    }

    @Test
    public void testCombinedFeatures() throws Exception {
        assertExpectedGMM("combined-features");
    }

    @Test
    public void testPlatformDependencies() throws Exception {
        assertExpectedGMM("platform-dependencies");
    }

    @Test
    public void testSnapshotStatusAttribute() throws Exception {
        assertExpectedGMM("snapshot-status-attribute");
    }

    @Test
    public void testVariantDependencies() throws Exception {
        assertExpectedGMM("variant-dependencies");
    }

    @Test
    public void testParentDependencies() throws Exception {
        assertExpectedGMM("parent-dependencies");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void assertExpectedGMM(String name) throws Exception  {
        File testProject = new File("target/test-classes/" + name);

        File gmmExpected = new File(testProject, "expected-module.json");
        assertTrue(gmmExpected.exists());

        File testJar = new File(testProject, "target/jar/example-0.1.jar");
        testJar.getParentFile().mkdirs();
        testJar.createNewFile();
        try (FileOutputStream stream = new FileOutputStream(testJar)) {
            stream.write(42);
        }

        assertNotNull(testProject);
        assertTrue(testProject.exists());

        GradleModuleMetadataMojo gmmMojo = (GradleModuleMetadataMojo) rule.lookupConfiguredMojo(testProject, "gmm");
        gmmMojo.project.getArtifact().setFile(testJar);

        assertNotNull(gmmMojo);
        gmmMojo.execute();

        File outputDirectory = (File) rule.getVariableValueFromObject(gmmMojo, "outputDirectory");
        assertNotNull(outputDirectory);
        assertTrue(outputDirectory.exists());

        File gmmActual = new File(outputDirectory, "module.json");
        assertTrue(gmmActual.exists());

        JsonElement expected = JsonParser.parseReader(new FileReader(gmmExpected));
        JsonElement actual = JsonParser.parseReader(new FileReader(gmmActual));
        assertEquals(expected, actual);
    }



}

