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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Output;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GraphToProjectConverterTest {

  private static final Context TEST_CONTEXT =
      new Context() {
        @Override
        public <T extends Output> void output(T output) {}

        @Override
        public void setHasError() {}
      };

  @Test
  public void testCalculateRootSources_singleSource_atImportRoot() throws IOException {

    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/Class1.java"), "com.test");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            TEST_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test")).containsExactly("", "com.test");
  }

  @Test
  public void testCalculateRootSources_singleSource_belowImportRoot() throws IOException {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/subpackage/Class1.java"), "com.test.subpackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            TEST_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test")).containsExactly("", "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_belowImportRoot() throws IOException {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package1/Class1.java"), "com.test.package1",
            Path.of("java/com/test/package2/Class2.java"), "com.test.package2");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            TEST_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test")).containsExactly("", "com.test");
  }

  @Test
  public void testCalculateRootSources_multiRoots() throws IOException {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/app/AppClass.java"), "com.app",
            Path.of("java/com/lib/LibClass.java"), "com.lib");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            TEST_CONTEXT,
            ImmutableList.of(Path.of("java/com/app"), Path.of("java/com/lib")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/app", "java/com/lib");
    assertThat(rootSources.get("java/com/app")).containsExactly("", "com.app");
    assertThat(rootSources.get("java/com/lib")).containsExactly("", "com.lib");
  }

  @Test
  public void testCalculateRootSources_multiSource_packageMismatch() throws IOException {
    // TODO(b/266538303) this test will fail if we swap `package1` and `package2` (i.e. such that
    //  their lexigraphic order is reversed), due to issues in GraphToProjectConverter. Fix those
    //  issues and add more test cases accordingly
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package2/Class1.java"), "com.test.package2",
            Path.of("java/com/test/package1/Class2.java"), "com.test.oddpackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            TEST_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test"))
        .containsExactly(
            "", "com.test",
            "package1", "com.test.oddpackage");
  }

  @Test
  public void testCalculateRootSources_multiSource_repackagedSource() throws IOException {
    // TODO(b/266538303) This test would fail if the lexicographic order of `repackaged` and
    //  `somepackage` was reversed, due to issues in GraphToProjectConverter
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/repackaged/com/foo/Class1.java"), "com.foo",
            Path.of("java/com/test/somepackage/Class2.java"), "com.test.somepackage");

    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            sourcePackages::get,
            TEST_CONTEXT,
            ImmutableList.of(Path.of("java/com/test")),
            ImmutableList.of());

    Map<String, Map<String, String>> rootSources =
        converter.calculateRootSources(sourcePackages.keySet());
    assertThat(rootSources.keySet()).containsExactly("java/com/test");
    assertThat(rootSources.get("java/com/test"))
        .containsExactly(
            "repackaged", "",
            "", "com.test");
  }
}