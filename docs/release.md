# Releasing

Pushing a version tag builds and publishes everything (WH-202,
[`.github/workflows/release.yml`](../.github/workflows/release.yml)):

| Job     | Runner  | Output                                                                                  |
| ------- | ------- | --------------------------------------------------------------------------------------- |
| Android | Ubuntu  | Signed `waveheaven-lambda-<version>.apk` and `.aab`, verified with `apksigner`          |
| Desktop | Ubuntu  | `.deb` and `.rpm`                                                                       |
| Desktop | Windows | `.msi`                                                                                  |
| Release | Ubuntu  | A GitHub Release with the files and notes generated from the commits since the last tag |

## Cutting a release

1. Make sure `main` is green and the README status table and docs are up to date.
2. Preview the notes: `uvx git-cliff --unreleased` (configuration in [`cliff.toml`](../cliff.toml)).
3. Tag and push:

   ```bash
   git switch main && git pull
   git tag -a v0.2.0 -m "WaveHeaven Lambda 0.2.0"
   git push origin v0.2.0
   ```

4. Follow the _Release_ workflow in the Actions tab. The GitHub Release appears when all jobs pass.

## Versions

- Tags follow [semantic versioning](https://semver.org): `v0.2.0`, `v1.0.0`, or `v0.2.0-beta.1` for a pre-release
  (published as such).
- The workflow passes the tag to Gradle as `-PappVersion=0.2.0`; local builds use `0.1.0`
  (`build-logic/…/AppVersion.kt`).
- Android: `versionName` is the full version, `versionCode` is `MAJOR·10000 + MINOR·100 + PATCH` (`0.2.0` → 200),
  so minor and patch must stay below 100. Pre-releases share the code of their final version, so install a beta over
  the previous release, not over the final one.
- Desktop installers take `MAJOR.MINOR.PATCH` without the pre-release suffix, which MSI, DEB and RPM require.

## Release notes

[git-cliff](https://git-cliff.org) strips the gitmoji and groups commits by Conventional Commits type: features,
fixes, performance, refactoring, documentation, build and CI, dependencies. `chore`, `test` and `style` commits are
left out. Because `main` is squash-merged, each entry is one pull request with its number. Good PR titles are good
release notes.

## Android signing

The upload key lives only in GitHub secrets, never in the repository (`*.jks` and `*.keystore` are ignored).

| Secret                      | Content                           |
| --------------------------- | --------------------------------- |
| `ANDROID_KEYSTORE_BASE64`   | The keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password                 |
| `ANDROID_KEY_ALIAS`         | Key alias                         |
| `ANDROID_KEY_PASSWORD`      | Key password                      |

Create the key once and keep a copy of the file and passwords somewhere safe outside the repository (a password
manager): losing it means users must uninstall to update.

```bash
keytool -genkeypair -keystore waveheaven-release.jks -alias waveheaven \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=WaveHeaven Lambda"
base64 -w0 waveheaven-release.jks   # paste the output into ANDROID_KEYSTORE_BASE64
```

Add the four secrets under _Settings › Secrets and variables › Actions_. Without them the Android job stops with a
message naming the missing secret.

To build a signed release locally, export the same values, with `ANDROID_KEYSTORE_FILE` pointing at the `.jks` file,
and run `./gradlew :apps:android:assembleRelease -PappVersion=0.2.0`. Without `ANDROID_KEYSTORE_FILE` the release
build is unsigned.

If the app is published on Google Play later, enrol in Play App Signing: this key then becomes the upload key.

## Desktop installers

- **Linux:** the DEB and RPM need `pactl` and `parec` at runtime (`pulseaudio-utils` on Debian and Ubuntu,
  `pulseaudio-utils` or `pipewire-pulseaudio` on Fedora), see [ADR-0009](adr/0009-desktop-per-app-volume.md).
- **Windows:** the MSI is not code-signed, so SmartScreen warns on first launch. Signing needs a certificate, which
  the project does not have yet.
