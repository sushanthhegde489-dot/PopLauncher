# PopLauncher — Release Runbook

## 1. Prepare the hosted privacy policy

Publish the root `privacy-policy.html` over HTTPS, for example through the same GitHub Pages site used for `custom-widgets/manifest.json`. Replace the placeholder `privacy_policy_url` string in:

`app/src/main/res/values/strings.xml`

Do not ship the placeholder URL.

## 2. Configure the Play upload key

Use the Play upload keystore, not a committed private key:

```bash
export PLAY_UPLOAD_STORE_FILE=/absolute/path/to/upload-key.jks
export PLAY_UPLOAD_STORE_PASSWORD='...'
export PLAY_UPLOAD_KEY_ALIAS='...'
export PLAY_UPLOAD_KEY_PASSWORD='...'
```

## 3. Build the bundle

```bash
./gradlew clean testDebugUnitTest bundleRelease
```

Expected primary artifact:

`app/build/outputs/bundle/release/app-release.aab`

If the exact output name differs, use the `.aab` generated in that directory.

## 4. Before upload

- Confirm versionCode/versionName are the intended release values.
- Confirm the final manifest contains only the declared permissions.
- Confirm the hosted HTTPS privacy-policy URL is live.
- Confirm Play Console Data Safety matches the final binary.
- Complete the `QUERY_ALL_PACKAGES` declaration for the launcher use case.
- **CRITICAL PLAY STORE COMPLIANCE**: Ensure `READ_CALL_LOG` and `READ_SMS` permissions are removed from the `AndroidManifest.xml` (these permissions are restricted and typically denied for launchers on the Play Store). Use the new SMS/Call log intent handoffs instead.
- Check the final AAB on a physical Android 16 device and at least one older supported Android version.
- Test setting/unsetting PopLauncher as the default Home app.
- Test first-run search without Contacts permission.
- Test explicit Contacts permission grant/revoke.
- Test app/widget pinning from another app, including the bind-permission flow.
- Test the optional Custom Widgets catalog, including cache/offline behavior, invalid manifest rejection, explicit Add/Update/Ignore/Remove, and no auto-install.
- Test a fresh install and an upgrade over the previous release.
- Test R8/minified behavior, especially widget JSON parsing and Compose screens.

## 5. Play-specific notes

`QUERY_ALL_PACKAGES` is a restricted permission; the launcher use case must be accurately declared.

The app targets API 36. Current Play requirements for new apps and updates submitted after 2026-08-31 require API 36 or higher.

The project uses current AGP/Gradle compatibility for API 37 compilation; AGP 9.4.x pairs with Gradle 9.6.x.
