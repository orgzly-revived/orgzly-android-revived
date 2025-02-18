{
  description = "Orgly Android nix build";
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };
  outputs = {
    nixpkgs,
    flake-utils,
    ...
  }:
  flake-utils.lib.eachDefaultSystem (system: let
    pkgs = import nixpkgs {
      inherit system;
      config = {
        android_sdk.accept_license = true;
        allowUnfree = true;
      };
    };
    cmakeVersion = "3.22.1";
    buildToolsVersion = "34.0.0";
    cmdLineToolsVersion = "8.0";
    androidComposition = pkgs.androidenv.composeAndroidPackages {
        cmdLineToolsVersion = cmdLineToolsVersion;
        toolsVersion = "26.1.1";
        platformToolsVersion = "34.0.5";
        buildToolsVersions = ["30.0.3" "33.0.1" buildToolsVersion];
        includeEmulator = true;
        platformVersions = ["30" "33" "34"];
        includeSources = false;
        includeSystemImages = true;
        systemImageTypes = ["google_apis_playstore"];
        abiVersions = ["x86-64" "x86_64"];
        cmakeVersions = [cmakeVersion];
        includeNDK = true;
        ndkVersions = ["23.1.7779620" "25.1.8937393" "26.1.10909125"];
        useGoogleAPIs = true;
        useGoogleTVAddOns = false;
        includeExtras = [
          "extras;google;gcm"
        ];
    };
    sharedDeps = with pkgs; [
      alejandra
      just
    ];
    android-sdk = androidComposition.androidsdk;
    android-home = "${androidComposition.androidsdk}/libexec/android-sdk";
    cmakeDir = "${android-home}/cmake/${cmakeVersion}/bin";
    emulator = "${android-home}/emulator";
    cmdLineTools = "${android-home}/cmdline-tools/${cmdLineToolsVersion}/bin";
    aapt2Binary = "${android-home}/build-tools/${buildToolsVersion}/aapt2";
    additionalPath = builtins.concatStringsSep ":" [cmakeDir emulator cmdLineTools];
  in {
    devShells = with pkgs; {
      default = mkShell {
        buildInputs = sharedDeps ++ [gradle_8 watchman];
        LC_ALL = "en_US.UTF-8";
        LANG = "en_US.UTF-8";
        CMAKE_DIR = cmakeDir;
        ANDROID_HOME = android-home;
        ANDROID_NDK_ROOT = "${android-home}/ndk-bundle";
        ANDROID_SDK_BIN = android-home;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2Binary}";
        shellHook =
          ''
            export JAVA_HOME=${pkgs.jdk17.home};
            source ${android-sdk.out}/nix-support/setup-hook
            export ORG_GRADLE_PROJECT_ANDROID_HOME="$ANDROID_HOME"
            export PATH=${additionalPath}:$PATH
          '';
      };
    };
  });
}
