/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules;

import com.facebook.buck.android.AndroidAarDescription;
import com.facebook.buck.android.AndroidBinary;
import com.facebook.buck.android.AndroidBinaryDescription;
import com.facebook.buck.android.AndroidBuildConfigDescription;
import com.facebook.buck.android.AndroidInstrumentationApkDescription;
import com.facebook.buck.android.AndroidLibraryDescription;
import com.facebook.buck.android.AndroidManifestDescription;
import com.facebook.buck.android.AndroidPrebuiltAarDescription;
import com.facebook.buck.android.AndroidResourceDescription;
import com.facebook.buck.android.ApkGenruleDescription;
import com.facebook.buck.android.GenAidlDescription;
import com.facebook.buck.android.NdkCxxPlatform;
import com.facebook.buck.android.NdkLibraryDescription;
import com.facebook.buck.android.PrebuiltNativeLibraryDescription;
import com.facebook.buck.android.ProGuardConfig;
import com.facebook.buck.android.RobolectricTestDescription;
import com.facebook.buck.apple.AppleAssetCatalogDescription;
import com.facebook.buck.apple.AppleBinaryDescription;
import com.facebook.buck.apple.AppleBundleDescription;
import com.facebook.buck.apple.AppleConfig;
import com.facebook.buck.apple.AppleCxxPlatform;
import com.facebook.buck.apple.AppleLibraryDescription;
import com.facebook.buck.apple.AppleResourceDescription;
import com.facebook.buck.apple.AppleSdk;
import com.facebook.buck.apple.AppleSdkDiscovery;
import com.facebook.buck.apple.AppleSdkPaths;
import com.facebook.buck.apple.AppleTestDescription;
import com.facebook.buck.apple.AppleToolchainDiscovery;
import com.facebook.buck.apple.CoreDataModelDescription;
import com.facebook.buck.apple.IosPostprocessResourcesDescription;
import com.facebook.buck.apple.XcodeProjectConfigDescription;
import com.facebook.buck.apple.XcodeWorkspaceConfigDescription;
import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cxx.CxxBinaryDescription;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPythonExtensionDescription;
import com.facebook.buck.cxx.CxxTestDescription;
import com.facebook.buck.cxx.DefaultCxxPlatform;
import com.facebook.buck.cxx.PrebuiltCxxLibraryDescription;
import com.facebook.buck.extension.BuckExtensionDescription;
import com.facebook.buck.file.Downloader;
import com.facebook.buck.file.ExplodingDownloader;
import com.facebook.buck.file.HttpDownloader;
import com.facebook.buck.file.RemoteFileDescription;
import com.facebook.buck.gwt.GwtBinaryDescription;
import com.facebook.buck.java.JavaBinaryDescription;
import com.facebook.buck.java.JavaBuckConfig;
import com.facebook.buck.java.JavaLibraryDescription;
import com.facebook.buck.java.JavaTestDescription;
import com.facebook.buck.java.Javac;
import com.facebook.buck.java.JavacOptions;
import com.facebook.buck.java.KeystoreDescription;
import com.facebook.buck.java.PrebuiltJarDescription;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.ocaml.OCamlBinaryDescription;
import com.facebook.buck.ocaml.OCamlBuckConfig;
import com.facebook.buck.ocaml.OCamlLibraryDescription;
import com.facebook.buck.ocaml.PrebuiltOCamlLibraryDescription;
import com.facebook.buck.parcelable.GenParcelableDescription;
import com.facebook.buck.python.PythonBinaryDescription;
import com.facebook.buck.python.PythonBuckConfig;
import com.facebook.buck.python.PythonEnvironment;
import com.facebook.buck.python.PythonLibraryDescription;
import com.facebook.buck.python.PythonTestDescription;
import com.facebook.buck.shell.ExportFileDescription;
import com.facebook.buck.shell.GenruleDescription;
import com.facebook.buck.shell.ShBinaryDescription;
import com.facebook.buck.shell.ShTestDescription;
import com.facebook.buck.thrift.ThriftBuckConfig;
import com.facebook.buck.thrift.ThriftCxxEnhancer;
import com.facebook.buck.thrift.ThriftJavaEnhancer;
import com.facebook.buck.thrift.ThriftLibraryDescription;
import com.facebook.buck.thrift.ThriftPythonEnhancer;
import com.facebook.buck.android.AndroidDirectoryResolver;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A registry of all the build rules types understood by Buck.
 */
public class KnownBuildRuleTypes {

  private final ImmutableMap<BuildRuleType, Description<?>> descriptions;
  private final ImmutableMap<String, BuildRuleType> types;

  private KnownBuildRuleTypes(
      Map<BuildRuleType, Description<?>> descriptions,
      Map<String, BuildRuleType> types) {
    this.descriptions = ImmutableMap.copyOf(descriptions);
    this.types = ImmutableMap.copyOf(types);
  }

  public BuildRuleType getBuildRuleType(String named) {
    BuildRuleType type = types.get(named);
    if (type == null) {
      throw new HumanReadableException("Unable to find build rule type: " + named);
    }
    return type;
  }

  public Description<?> getDescription(BuildRuleType buildRuleType) {
    Description<?> description = descriptions.get(buildRuleType);
    if (description == null) {
      throw new HumanReadableException(
          "Unable to find description for build rule type: " + buildRuleType);
    }
    return description;
  }

  public ImmutableSet<Description<?>> getAllDescriptions() {
    return ImmutableSet.copyOf(descriptions.values());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static KnownBuildRuleTypes createInstance(
      BuckConfig config,
      ProcessExecutor processExecutor,
      AndroidDirectoryResolver androidDirectoryResolver,
      PythonEnvironment pythonEnv) throws InterruptedException, IOException {
    return createBuilder(config, processExecutor, androidDirectoryResolver, pythonEnv).build();
  }

  /**
   * @return the map holding the available {@link NdkCxxPlatform}s.
   */
  private static ImmutableMap<AndroidBinary.TargetCpuType, NdkCxxPlatform> getNdkCxxPlatforms(
      Path ndkRoot,
      Platform platform) {

    ImmutableMap.Builder<AndroidBinary.TargetCpuType, NdkCxxPlatform> ndkCxxPlatformBuilder =
        ImmutableMap.builder();

    NdkCxxPlatform armeabi =
        new NdkCxxPlatform(
            new Flavor("android-arm"),
            platform,
            ndkRoot,
            new NdkCxxPlatform.TargetConfiguration(
                NdkCxxPlatform.Toolchain.ARM_LINUX_ADNROIDEABI_4_8,
                NdkCxxPlatform.ToolchainPrefix.ARM_LINUX_ANDROIDEABI,
                NdkCxxPlatform.TargetArch.ARM,
                NdkCxxPlatform.TargetArchAbi.ARMEABI,
                /* androidPlatform */ "android-9",
                /* compilerVersion */ "4.8",
                /* compilerFlags */ ImmutableList.of(
                    "-march=armv5te",
                    "-mtune=xscale",
                    "-msoft-float",
                    "-mthumb",
                    "-Os")),
            NdkCxxPlatform.CxxRuntime.GNUSTL);
    ndkCxxPlatformBuilder.put(AndroidBinary.TargetCpuType.ARM, armeabi);
    NdkCxxPlatform armeabiv7 =
        new NdkCxxPlatform(
            new Flavor("android-armv7"),
            platform,
            ndkRoot,
            new NdkCxxPlatform.TargetConfiguration(
                NdkCxxPlatform.Toolchain.ARM_LINUX_ADNROIDEABI_4_8,
                NdkCxxPlatform.ToolchainPrefix.ARM_LINUX_ANDROIDEABI,
                NdkCxxPlatform.TargetArch.ARM,
                NdkCxxPlatform.TargetArchAbi.ARMEABI_V7A,
                /* androidPlatform */ "android-9",
                /* compilerVersion */ "4.8",
                /* compilerFlags */ ImmutableList.of(
                    "-finline-limit=64",
                    "-march=armv7-a",
                    "-mfpu=vfpv3-d16",
                    "-mfloat-abi=softfp",
                    "-mthumb",
                    "-Os")),
            NdkCxxPlatform.CxxRuntime.GNUSTL);
    ndkCxxPlatformBuilder.put(AndroidBinary.TargetCpuType.ARMV7, armeabiv7);
    NdkCxxPlatform x86 =
        new NdkCxxPlatform(
            new Flavor("android-x86"),
            platform,
            ndkRoot,
            new NdkCxxPlatform.TargetConfiguration(
                NdkCxxPlatform.Toolchain.X86_4_8,
                NdkCxxPlatform.ToolchainPrefix.I686_LINUX_ANDROID,
                NdkCxxPlatform.TargetArch.X86,
                NdkCxxPlatform.TargetArchAbi.X86,
                /* androidPlatform */ "android-9",
                /* compilerVersion */ "4.8",
                /* compilerFlags */ ImmutableList.of(
                    "-funswitch-loops",
                    "-finline-limit=300",
                    "-O2")),
            NdkCxxPlatform.CxxRuntime.GNUSTL);
    ndkCxxPlatformBuilder.put(AndroidBinary.TargetCpuType.X86, x86);

    return ndkCxxPlatformBuilder.build();
  }

  private static void buildAppleCxxPlatforms(
      Supplier<Path> appleDeveloperDirectorySupplier,
      Platform buildPlatform,
      ImmutableSet.Builder<CxxPlatform> appleCxxPlatformsBuilder)
      throws IOException {
    if (!buildPlatform.equals(Platform.MACOS)) {
      return;
    }

    Path appleDeveloperDirectory = appleDeveloperDirectorySupplier.get();
    if (!Files.isDirectory(appleDeveloperDirectory)) {
      // TODO(user): This should be fatal, but a ton of integration tests enter this code on
      // Apple platforms.
      return;
    }

    ImmutableMap<String, Path> toolchainPaths = AppleToolchainDiscovery.discoverAppleToolchainPaths(
        appleDeveloperDirectory);

    ImmutableMap<AppleSdk, AppleSdkPaths> sdkPaths = AppleSdkDiscovery.discoverAppleSdkPaths(
        appleDeveloperDirectory,
        toolchainPaths);

    for (Map.Entry<AppleSdk, AppleSdkPaths> entry : sdkPaths.entrySet()) {
      AppleSdk sdk = entry.getKey();
      for (String architecture : sdk.architectures()) {
        CxxPlatform appleCxxPlatform = new AppleCxxPlatform(
            buildPlatform,
            sdk.applePlatform(),
            sdk.name(),
            // TODO(user): Support targeting earlier OS versions; this
            // targets the exact version of the SDK.
            sdk.version(),
            architecture,
            entry.getValue());
        appleCxxPlatformsBuilder.add(appleCxxPlatform);
      }
    }
  }

  @VisibleForTesting
  static Builder createBuilder(
      BuckConfig config,
      ProcessExecutor processExecutor,
      AndroidDirectoryResolver androidDirectoryResolver,
      PythonEnvironment pythonEnv) throws InterruptedException, IOException {

    Platform platform = Platform.detect();

    Optional<String> ndkVersion = config.getNdkVersion();
    // If a NDK version isn't specified, we've got to reach into the runtime environment to find
    // out which one we will end up using.
    if (!ndkVersion.isPresent()) {
      ndkVersion = androidDirectoryResolver.getNdkVersion();
    }

    AppleConfig appleConfig = new AppleConfig(config);
    ImmutableSet.Builder<CxxPlatform> appleCxxPlatformsBuilder =
        ImmutableSet.builder();
    buildAppleCxxPlatforms(
        appleConfig.getAppleDeveloperDirectorySupplier(processExecutor),
        platform,
        appleCxxPlatformsBuilder);
    ImmutableSet<CxxPlatform> appleCxxPlatforms = appleCxxPlatformsBuilder.build();

    // Construct the thrift config wrapping the buck config.
    ThriftBuckConfig thriftBuckConfig = new ThriftBuckConfig(config);

    // Construct the OCaml config wrapping the buck config.
    OCamlBuckConfig ocamlBuckConfig = new OCamlBuckConfig(platform, config);

    // Setup the NDK C/C++ platforms.
    ImmutableMap.Builder<AndroidBinary.TargetCpuType, NdkCxxPlatform> ndkCxxPlatformsBuilder =
        ImmutableMap.builder();
    Optional<Path> ndkRoot = androidDirectoryResolver.findAndroidNdkDir();
    if (ndkRoot.isPresent()) {
      ndkCxxPlatformsBuilder.putAll(getNdkCxxPlatforms(ndkRoot.get(), platform));
    }
    ImmutableMap<AndroidBinary.TargetCpuType, NdkCxxPlatform> ndkCxxPlatforms =
        ndkCxxPlatformsBuilder.build();

    // Construct the C/C++ config wrapping the buck config.
    CxxBuckConfig cxxBuckConfig = new CxxBuckConfig(config);
    ImmutableMap.Builder<Flavor, CxxPlatform> cxxPlatformsBuilder = ImmutableMap.builder();

    // Add the default, config-defined C/C++ platform.
    DefaultCxxPlatform defaultCxxPlatform = new DefaultCxxPlatform(platform, config);
    cxxPlatformsBuilder.put(defaultCxxPlatform.asFlavor(), defaultCxxPlatform);

    // If an Android NDK is present, add platforms for that.  This is mostly useful for
    // testing our Android NDK support for right now.
    for (CxxPlatform ndkCxxPlatform : ndkCxxPlatforms.values()) {
      cxxPlatformsBuilder.put(ndkCxxPlatform.asFlavor(), ndkCxxPlatform);
    }

    for (CxxPlatform appleCxxPlatform : appleCxxPlatforms) {
      cxxPlatformsBuilder.put(appleCxxPlatform.asFlavor(), appleCxxPlatform);
    }

    // Build up the final list of C/C++ platforms.
    FlavorDomain<CxxPlatform> cxxPlatforms = new FlavorDomain<>(
        "C/C++ platform",
        cxxPlatformsBuilder.build());

    ProGuardConfig proGuardConfig = new ProGuardConfig(config);

    PythonBuckConfig pyConfig = new PythonBuckConfig(config);
    // Look up the path to the PEX builder script.
    Optional<Path> pythonPathToPex = pyConfig.getPathToPex();

    // Look up the path to the main module we use for python tests.
    Optional<Path> pythonPathToPythonTestMain = pyConfig.getPathToTestMain();

    // Look up the timeout to apply to entire test rules.
    Optional<Long> testRuleTimeoutMs = config.getLong("test", "rule_timeout");

    // Default maven repo, if set
    Optional<String> defaultMavenRepo = config.getValue("download", "maven_repo");
    boolean downloadAtRuntimeOk = config.getBooleanValue("download", "in_build", false);
    Downloader downloader;
    if (downloadAtRuntimeOk) {
      downloader = new HttpDownloader(Optional.<Proxy>absent(), defaultMavenRepo);
    } else {
      downloader = new ExplodingDownloader();
    }

    Builder builder = builder();

    JavaBuckConfig javaConfig = new JavaBuckConfig(config);
    Javac javac = javaConfig.getJavac();
    JavacOptions defaultJavacOptions = javaConfig.getDefaultJavacOptions(processExecutor);
    JavacOptions androidBinaryOptions = JavacOptions.builder(defaultJavacOptions)
        .build();

    CxxBinaryDescription cxxBinaryDescription = new CxxBinaryDescription(
        cxxBuckConfig,
        defaultCxxPlatform,
        cxxPlatforms);

    CxxLibraryDescription cxxLibraryDescription = new CxxLibraryDescription(
        cxxBuckConfig,
        cxxPlatforms);

    AppleLibraryDescription appleLibraryDescription =
        new AppleLibraryDescription(appleConfig, cxxLibraryDescription);
    builder.register(appleLibraryDescription);

    builder.register(new AndroidAarDescription());
    builder.register(
        new AndroidBinaryDescription(
            javac,
            androidBinaryOptions,
            proGuardConfig,
            ndkCxxPlatforms));
    builder.register(new AndroidBuildConfigDescription(javac, androidBinaryOptions));
    builder.register(new AndroidInstrumentationApkDescription(
            proGuardConfig,
            javac,
            androidBinaryOptions,
            ndkCxxPlatforms));
    builder.register(new AndroidLibraryDescription(javac, androidBinaryOptions));
    builder.register(new AndroidManifestDescription());
    builder.register(new AndroidPrebuiltAarDescription(javac, androidBinaryOptions));
    builder.register(new AndroidResourceDescription());
    builder.register(new ApkGenruleDescription());
    builder.register(new AppleAssetCatalogDescription());
    builder.register(new AppleBinaryDescription(appleConfig, cxxBinaryDescription));
    builder.register(new AppleBundleDescription());
    builder.register(new AppleLibraryDescription(appleConfig, cxxLibraryDescription));
    builder.register(new AppleResourceDescription());
    builder.register(new AppleTestDescription(appleLibraryDescription));
    builder.register(new BuckExtensionDescription(javac, defaultJavacOptions));
    builder.register(new CoreDataModelDescription());
    builder.register(cxxBinaryDescription);
    builder.register(cxxLibraryDescription);
    builder.register(new CxxPythonExtensionDescription(cxxBuckConfig, cxxPlatforms));
    builder.register(new CxxTestDescription(cxxBuckConfig, defaultCxxPlatform, cxxPlatforms));
    builder.register(new ExportFileDescription());
    builder.register(new GenruleDescription());
    builder.register(new GenAidlDescription());
    builder.register(new GenParcelableDescription());
    builder.register(new GwtBinaryDescription());
    builder.register(new IosPostprocessResourcesDescription());
    builder.register(new JavaBinaryDescription(javac, defaultJavacOptions, defaultCxxPlatform));
    builder.register(new JavaLibraryDescription(javac, defaultJavacOptions));
    builder.register(new JavaTestDescription(javac, defaultJavacOptions, testRuleTimeoutMs));
    builder.register(new KeystoreDescription());
    builder.register(new NdkLibraryDescription(ndkVersion, ndkCxxPlatforms));
    builder.register(new OCamlBinaryDescription(ocamlBuckConfig));
    builder.register(new OCamlLibraryDescription(ocamlBuckConfig));
    builder.register(new PrebuiltCxxLibraryDescription(cxxPlatforms));
    builder.register(new PrebuiltJarDescription());
    builder.register(new PrebuiltNativeLibraryDescription());
    builder.register(new PrebuiltOCamlLibraryDescription());
    builder.register(new ProjectConfigDescription());
    builder.register(
        new PythonBinaryDescription(
            pythonPathToPex.or(PythonBinaryDescription.DEFAULT_PATH_TO_PEX),
            pythonEnv,
            defaultCxxPlatform,
            cxxPlatforms));
    builder.register(new PythonLibraryDescription());
    builder.register(
        new PythonTestDescription(
            pythonPathToPex.or(PythonBinaryDescription.DEFAULT_PATH_TO_PEX),
            pythonPathToPythonTestMain,
            pythonEnv,
            defaultCxxPlatform,
            cxxPlatforms));
    builder.register(new RemoteFileDescription(downloader));
    builder.register(new RobolectricTestDescription(
            javac,
            androidBinaryOptions,
            testRuleTimeoutMs));
    builder.register(new ShBinaryDescription());
    builder.register(new ShTestDescription());
    builder.register(
        new ThriftLibraryDescription(
            thriftBuckConfig,
            ImmutableList.of(
                new ThriftJavaEnhancer(thriftBuckConfig, javac, defaultJavacOptions),
                new ThriftCxxEnhancer(
                    thriftBuckConfig,
                    cxxBuckConfig,
                    cxxPlatforms,
                    /* cpp2 */ false),
                new ThriftCxxEnhancer(
                    thriftBuckConfig,
                    cxxBuckConfig,
                    cxxPlatforms,
                    /* cpp2 */ true),
                new ThriftPythonEnhancer(thriftBuckConfig, ThriftPythonEnhancer.Type.NORMAL),
                new ThriftPythonEnhancer(thriftBuckConfig, ThriftPythonEnhancer.Type.TWISTED))));
    builder.register(new XcodeProjectConfigDescription());
    builder.register(new XcodeWorkspaceConfigDescription());

    return builder;
  }

  public static class Builder {
    private final Map<BuildRuleType, Description<?>> descriptions;
    private final Map<String, BuildRuleType> types;

    protected Builder() {
      this.descriptions = Maps.newConcurrentMap();
      this.types = Maps.newConcurrentMap();
    }

    public void register(Description<?> description) {
      BuildRuleType type = description.getBuildRuleType();
      types.put(type.getName(), type);
      descriptions.put(type, description);
    }

    public KnownBuildRuleTypes build() {
      return new KnownBuildRuleTypes(descriptions, types);
    }
  }
}
