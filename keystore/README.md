# Release signing key

`release.jks` signs release builds. Referenced by `../keystore.properties`
(repo root, gitignored) via `app/build.gradle.kts`.

- Alias: `tasbih_counter_release`
- Type: PKCS12, RSA 2048, valid until 2051-08-17
- SHA256 fingerprint: `02:86:2A:CD:29:95:87:29:D9:F4:9E:88:94:CF:07:AA:07:07:86:57:04:A5:DD:D2:84:6D:21:BD:95:F0:23:9F`

## CRITICAL — back this up

Neither `keystore/` nor `keystore.properties` is committed (see `.gitignore`).
If this machine is lost and no backup exists, you **cannot ever again**
publish an update to an app already released under this key — Play Store
requires the same signing key for every update, with no recovery path.

Back up both files (keystore/release.jks + keystore.properties) to a
password manager or offline encrypted storage, not just this disk.

## Regenerating (only if you intend a NEW key, e.g. first-ever release)

```
keytool -genkeypair -v -keystore keystore/release.jks \
  -alias tasbih_counter_release -keyalg RSA -keysize 2048 -validity 9125
```

Then create `keystore.properties` at repo root:

```
storeFile=keystore/release.jks
storePassword=<store password>
keyAlias=tasbih_counter_release
keyPassword=<same as storePassword — PKCS12 requires it>
```
