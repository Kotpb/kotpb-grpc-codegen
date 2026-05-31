# Changelog

## [0.2.2](https://github.com/Kotpb/kotpb-grpc-codegen/compare/v0.2.1...v0.2.2) (2026-05-31)


### Build & CI

* **deps:** Bump the gradle-deps group with 2 updates ([#12](https://github.com/Kotpb/kotpb-grpc-codegen/issues/12)) ([a3745a2](https://github.com/Kotpb/kotpb-grpc-codegen/commit/a3745a20c94fc35be1fda9ec8fd60bc6e4b7e447))

## [0.2.1](https://github.com/Kotpb/kotpb-grpc-codegen/compare/v0.2.0...v0.2.1) (2026-05-24)


### Documentation

* Document branch protection + drop stale Maven Central setup ([#6](https://github.com/Kotpb/kotpb-grpc-codegen/issues/6)) ([ff6b9dd](https://github.com/Kotpb/kotpb-grpc-codegen/commit/ff6b9dd7cfa3e1753dbfc95aa9d5d4ed5873020b))


### Build & CI

* **deps:** Bump the gh-actions group with 2 updates ([#9](https://github.com/Kotpb/kotpb-grpc-codegen/issues/9)) ([633b63e](https://github.com/Kotpb/kotpb-grpc-codegen/commit/633b63ed069f9b514339b2ee66e7650d0e4d05a6))
* **deps:** Bump the gradle-deps group across 1 directory with 5 updates ([#11](https://github.com/Kotpb/kotpb-grpc-codegen/issues/11)) ([3181139](https://github.com/Kotpb/kotpb-grpc-codegen/commit/31811399a22aca9e6e7d99c949f3ca0d95639b5f))
* **deps:** Bump the gradle-deps group with 2 updates ([#8](https://github.com/Kotpb/kotpb-grpc-codegen/issues/8)) ([33566f7](https://github.com/Kotpb/kotpb-grpc-codegen/commit/33566f796a7e1740276281c0ad5c449b748de95c))

## [0.2.0](https://github.com/Kotpb/kotpb-grpc-codegen/compare/v0.1.1...v0.2.0) (2026-05-04)


### Features

* Add JVM-fallback shadow JAR as :jvm@jar classifier ([b98f5fe](https://github.com/Kotpb/kotpb-grpc-codegen/commit/b98f5fe3a64aa9351c0bafe0ac75e4cc39183385))


### Bug Fixes

* Revert -Os; GraalVM 21 doesn't support it ([f127132](https://github.com/Kotpb/kotpb-grpc-codegen/commit/f1271328ea92f6f4e1054f7e636599bc9fe8f86f))


### Performance

* Drop the GC entirely on the native binary (--gc=epsilon) ([a674f5d](https://github.com/Kotpb/kotpb-grpc-codegen/commit/a674f5dc59b5dd9b293bda2e5c3b29e0d359a7df))
* Shrink shipped artifacts via shadowJar minimize + -Os ([414d70a](https://github.com/Kotpb/kotpb-grpc-codegen/commit/414d70a7badb48523b7d75b5a084ccddceb2644f))


### Build & CI

* Bump GraalVM 21 -&gt; 25 + switch native-image to -Os ([3486c94](https://github.com/Kotpb/kotpb-grpc-codegen/commit/3486c940cc090104b6950896c6178dd634711a55))
* Diagnose SIGNING_KEY structure before publish ([5d6bdf5](https://github.com/Kotpb/kotpb-grpc-codegen/commit/5d6bdf5516fee566d3c58b16fa3948491aa20120))

## [0.1.1](https://github.com/Kotpb/kotpb-grpc-codegen/compare/v0.1.0...v0.1.1) (2026-05-04)


### Bug Fixes

* Use Central Portal staging-api URL for Sonatype publishing ([fc859c0](https://github.com/Kotpb/kotpb-grpc-codegen/commit/fc859c04592a2c0f417dd94c9b0ae01b654979b9))


### Documentation

* Document the release process ([8cf08ae](https://github.com/Kotpb/kotpb-grpc-codegen/commit/8cf08aee21a08be1df617c6879c09aea38f06048))


### Build & CI

* add general build + test workflow and Dependabot config ([6643d33](https://github.com/Kotpb/kotpb-grpc-codegen/commit/6643d33f38d97e1095c1abeb4b29634ddb52da65))
* Add release-please workflow and Conventional-Commits PR-title lint ([1475e57](https://github.com/Kotpb/kotpb-grpc-codegen/commit/1475e5742eb3f731200100cc75a6aecaa37d5b69))
* Capitalize release-please PR title subject ([95429d3](https://github.com/Kotpb/kotpb-grpc-codegen/commit/95429d3f18953ef7036a7745368f569f0001856c))
* drop musl-static for the aarch64 linux native binary ([e78b2cd](https://github.com/Kotpb/kotpb-grpc-codegen/commit/e78b2cdaf32f5d5dccdc08d8a31856228ab83137))
* drop osx-x86_64 (Intel macOS) from native binaries matrix ([d30884c](https://github.com/Kotpb/kotpb-grpc-codegen/commit/d30884c4e38a568d13f92c4c92518af379a8750a))
* linux-aarch_64 native uses mostly-static, document the asymmetry ([3ab8b51](https://github.com/Kotpb/kotpb-grpc-codegen/commit/3ab8b516659636bfd458c8652403902b9a2b1789))
* Pin all GitHub Actions to commit SHAs ([6e28b1b](https://github.com/Kotpb/kotpb-grpc-codegen/commit/6e28b1b68557387f2965eee2420fda602e9b7060))
* Repair release pipeline for v0.1.1 re-cut ([712d4ee](https://github.com/Kotpb/kotpb-grpc-codegen/commit/712d4ee5b66b8fa3e30741ec19bcf220311045b0))
* setup-gradle@v6 in native-binaries (match ci.yml) ([0005263](https://github.com/Kotpb/kotpb-grpc-codegen/commit/00052638b3039532eda8e1e84c117e988ccbb350))
* skip :plugin:nativeCompile on the general workflow ([020bb36](https://github.com/Kotpb/kotpb-grpc-codegen/commit/020bb365cf2410458dbe51a86779753f328e902f))
* smoke-test by diffing native output vs JVM output ([e99b700](https://github.com/Kotpb/kotpb-grpc-codegen/commit/e99b7000facd846599a688f6fc997f48bd626300))
* smoke-test produced binaries + linux-aarch64 target ([dd8aa3b](https://github.com/Kotpb/kotpb-grpc-codegen/commit/dd8aa3b7b24e682dad3d8718dc951643efe0a820))
* Wire Maven Central publishing for the native binary ([bcbba35](https://github.com/Kotpb/kotpb-grpc-codegen/commit/bcbba35b0389c40e497e966ce93b38b332370729))

## Changelog
