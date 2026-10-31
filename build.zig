const std = @import("std");

const min_sdk = "24";
const target_sdk = "36";

// The key that signs the APK. Android only installs an update signed by the
// same key as the installed app. CI gets it from the RELEASE_KEYSTORE secret.
const keystore = "release.keystore";
const key_alias = "harpertube";
// The keystore file itself is the secret, so the password adds nothing.
const keystore_pass = "pass:harpertube";

pub fn build(b: *std.Build) void {
    const host = b.graph.host.result;
    const jdk_dep = b.lazyDependency(b.fmt("jdk_{t}_{t}", .{ host.os.tag, host.cpu.arch }), .{});
    const build_tools_dep = b.lazyDependency(b.fmt("build_tools_{t}", .{host.os.tag}), .{});
    const jdk: Jdk = .init(b, jdk_dep orelse return);
    const build_tools: BuildTools = .init(b, build_tools_dep orelse return);
    const android_jar = b.dependency("android_platform", .{}).path("android.jar");

    const compiled_res = blk: {
        const run = addTool(b, "aapt2", build_tools.aapt2);
        run.addArgs(&.{ "compile", "--dir" });
        run.addDirectoryArg(b.path("res"));
        run.addArg("-o");
        break :blk run.addOutputFileArg("res.zip");
    };

    // The manifest and compiled resources; the code is added below.
    const linked = blk: {
        const run = addTool(b, "aapt2", build_tools.aapt2);
        run.addArgs(&.{ "link", "--manifest" });
        run.addFileArg(b.path("AndroidManifest.xml"));
        run.addArg("-I");
        run.addFileArg(android_jar);
        run.addArgs(&.{
            "--min-sdk-version",    min_sdk,
            "--target-sdk-version", target_sdk,
            "--version-code",       "1",
            "--version-name",       "0.1",
            "-o",
        });
        const out = run.addOutputFileArg("linked.apk");
        run.addFileArg(compiled_res);
        break :blk out;
    };

    const classes = blk: {
        const run = addTool(b, "javac", jdk.javac);
        run.addArgs(&.{ "--release", "11", "-Werror", "-Xlint:all,-options", "-classpath" });
        run.addFileArg(android_jar);
        run.addArg("-d");
        const out = run.addOutputDirectoryArg("classes");
        run.addFileArg(b.path("src/io/github/marler8997/harpertube/MainActivity.java"));
        break :blk out;
    };

    // d8 doesn't take a directory of classes, so they go in a jar first.
    const classes_jar = blk: {
        const run = addTool(b, "jar", jdk.jar);
        run.addArgs(&.{ "--create", "--no-manifest", "--file" });
        const out = run.addOutputFileArg("classes.jar");
        run.addArg("-C");
        run.addDirectoryArg(classes);
        run.addArg(".");
        break :blk out;
    };

    const dex = blk: {
        const run = addTool(b, "java", jdk.java);
        run.addArg("-cp");
        run.addFileArg(build_tools.d8_jar);
        run.addArgs(&.{ "com.android.tools.r8.D8", "--release", "--min-api", min_sdk, "--lib" });
        run.addFileArg(android_jar);
        run.addArg("--output");
        const out = run.addOutputDirectoryArg("dex");
        run.addFileArg(classes_jar);
        break :blk out;
    };

    const unaligned = blk: {
        const run = addTool(b, "java", jdk.java);
        run.addFileArg(b.path("tools/ApkPack.java"));
        const out = run.addOutputFileArg("unaligned.apk");
        run.addFileArg(linked);
        run.addFileArg(dex.path(b, "classes.dex"));
        break :blk out;
    };

    const aligned = blk: {
        const run = addTool(b, "zipalign", build_tools.zipalign);
        run.addArgs(&.{ "-f", "4" });
        run.addFileArg(unaligned);
        break :blk run.addOutputFileArg("aligned.apk");
    };

    b.getInstallStep().dependOn(&b.addInstallFile(aligned, "HarperTube-unsigned.apk").step);

    const signed = blk: {
        const run = addTool(b, "java", jdk.java);
        run.addArg("-jar");
        run.addFileArg(build_tools.apksigner_jar);
        run.addArgs(&.{ "sign", "--v4-signing-enabled", "false", "--ks" });
        run.addFileArg(b.path(keystore));
        run.addArgs(&.{ "--ks-key-alias", key_alias, "--ks-pass", keystore_pass, "--out" });
        const out = run.addOutputFileArg("HarperTube.apk");
        run.addFileArg(aligned);
        break :blk out;
    };

    b.step("install-signed", "Build zig-out/HarperTube.apk, signed with " ++ keystore).dependOn(
        &b.addInstallFile(signed, "HarperTube.apk").step,
    );
}

const Jdk = struct {
    java: std.Build.LazyPath,
    javac: std.Build.LazyPath,
    jar: std.Build.LazyPath,

    fn init(b: *std.Build, dep: *std.Build.Dependency) Jdk {
        const host = b.graph.host.result;
        const bin = if (host.os.tag == .macos) "Contents/Home/bin/" else "bin/";
        const exe = host.exeFileExt();
        return .{
            .java = dep.path(b.fmt("{s}java{s}", .{ bin, exe })),
            .javac = dep.path(b.fmt("{s}javac{s}", .{ bin, exe })),
            .jar = dep.path(b.fmt("{s}jar{s}", .{ bin, exe })),
        };
    }
};

const BuildTools = struct {
    aapt2: std.Build.LazyPath,
    zipalign: std.Build.LazyPath,
    d8_jar: std.Build.LazyPath,
    apksigner_jar: std.Build.LazyPath,

    fn init(b: *std.Build, dep: *std.Build.Dependency) BuildTools {
        const host = b.graph.host.result;
        const exe = host.exeFileExt();
        return .{
            .aapt2 = dep.path(b.fmt("aapt2{s}", .{exe})),
            .zipalign = dep.path(b.fmt("zipalign{s}", .{exe})),
            .d8_jar = dep.path("lib/d8.jar"),
            .apksigner_jar = dep.path("lib/apksigner.jar"),
        };
    }
};

fn addTool(b: *std.Build, name: []const u8, tool: std.Build.LazyPath) *std.Build.Step.Run {
    const run = std.Build.Step.Run.create(b, name);
    run.addFileArg(tool);
    return run;
}
