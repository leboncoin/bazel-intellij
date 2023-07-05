/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.EMPTY_PACKAGE_READER;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectDefinition.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentEntry;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentRoot.Base;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.ProjectProto.SourceFolder;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GeneratedSourceProjectUpdaterTest {

  private static Project constructBasicProject() throws IOException {
    Path workspaceImportDirectory = TestData.ROOT.resolve("nodeps");
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            EMPTY_PACKAGE_READER,
            NOOP_CONTEXT,
            ProjectDefinition.create(
                ImmutableSet.of(workspaceImportDirectory),
                ImmutableSet.of(),
                ImmutableSet.of(LanguageClass.JAVA)));

    BuildGraphData buildGraphData =
        new BlazeQueryParser(NOOP_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY));
    return converter.createProject(buildGraphData);
  }

  @Test
  public void testNoGeneratedSources() throws IOException {
    Project project = constructBasicProject();

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(project, Paths.get(""), ImmutableList.of());
    Project newProject = updater.addGenSrcContentEntry();

    // Operation is a no-op that passes the proto through.
    assertThat(newProject).isSameInstanceAs(project);
  }

  @Test
  public void testGeneratedSourcesAdded() throws IOException {
    Project project = constructBasicProject();
    assertThat(project.getModulesCount()).isEqualTo(1);
    assertThat(project.getModules(0).getContentEntriesCount()).isEqualTo(1);

    Path projectPath = Paths.get("/my/project");
    Path genSrcCacheRelativePath = Paths.get("generated");

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(
            project,
            genSrcCacheRelativePath,
            ImmutableList.of(
                projectPath.resolve(genSrcCacheRelativePath).resolve("gensrc1"),
                projectPath.resolve(genSrcCacheRelativePath).resolve("gensrc2")));
    Project newProject = updater.addGenSrcContentEntry();
    assertThat(newProject.getModulesCount()).isEqualTo(1);
    assertThat(newProject.getModules(0).getContentEntriesCount()).isEqualTo(2);
    ContentEntry contentEntry = newProject.getModules(0).getContentEntries(1);
    assertThat(contentEntry.getRoot().getBase()).isEqualTo(Base.PROJECT);
    assertThat(contentEntry.getRoot().getPath()).isEqualTo("generated");

    assertThat(contentEntry.getSourcesCount()).isEqualTo(2);

    SourceFolder source1 = contentEntry.getSources(0);
    assertThat(source1.getPath()).isEqualTo("generated/gensrc1");
    assertThat(source1.getPackagePrefix()).isEmpty();
    assertThat(source1.getIsGenerated()).isTrue();

    SourceFolder source2 = contentEntry.getSources(1);
    assertThat(source2.getPath()).isEqualTo("generated/gensrc2");
    assertThat(source2.getPackagePrefix()).isEmpty();
    assertThat(source2.getIsGenerated()).isTrue();
  }
}
