# HarperTube APK

An Android app for [HarperTube](https://harpertube.github.io/).

## Building

```sh
zig build                  # writes zig-out/HarperTube-unsigned.apk
zig build install-signed   # writes zig-out/HarperTube.apk, signed with release.keystore
```

The build downloads the JDK and Android build-tools for your machine, pinned in `build.zig.zon`, so there's nothing else to install. It builds on x86_64 Windows and Linux, and on macOS.

## The signing key

The APK is signed with the key in `release.keystore`. Android only installs an update signed with the same key as the installed app, so there's only ever one key. Losing it means uninstalling the app before a new build will install.

`release.keystore` is the `RELEASE_KEYSTORE` GitHub secret (base64-encoded), which CI uses to sign. To run `zig build install-signed` locally, put a copy of `release.keystore` in the repository root; Johnny knows where to find it.
