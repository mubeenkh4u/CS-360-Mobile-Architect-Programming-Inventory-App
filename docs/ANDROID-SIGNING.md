# Android release signing

Auwire uses a dedicated Android release key so future APKs can update an
existing installation instead of behaving like unrelated debug builds.

## Local signed release

1. Keep `auwire-release-key.jks` outside the repository.
2. Copy `keystore.properties.example` to `keystore.properties`.
3. Point `storeFile` at the JKS and enter the passwords.
4. Run:

```bash
gradle --no-daemon :app:assembleRelease
```

The signed APK is produced under:

```
app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions

For signed release artifacts, configure these repository Actions secrets:

- `AUWIRE_KEYSTORE_B64` — base64 of the JKS file
- `AUWIRE_STORE_PASSWORD`
- `AUWIRE_KEY_ALIAS` — normally `auwire`
- `AUWIRE_KEY_PASSWORD`

The workflow continues to build/test the debug APK when release secrets are not
configured. When they are present, it additionally uploads
`Auwire-signed-release-apk`.

## Update rule

Android accepts an in-place APK update only when both the application ID and the
signing identity match the installed app. The package ID remains:

```
com.auwire.iamkhata
```

Earlier GitHub debug APKs used debug signing. The first move to this dedicated
release key therefore requires uninstalling the debug-signed installation (or
using Android tooling to preserve/restore data where appropriate). After the
release-signed build is installed, keep this exact key permanently: every future
update must use it.

Never commit the JKS or passwords to Git.
