# AWi&k Builds

This directory is populated automatically by GitHub Actions after a successful
**signed release build** from `feature/khata-system`.

Each application version gets its own directory:

```text
Builds/
└── v5.5-final-icon-assets/
    ├── AWi-k-v5.5-final-icon-assets-vc10-<commit>-release.apk
    ├── AWi-k-v5.5-final-icon-assets-vc10-<commit>-release.apk.sha256
    └── AWi-k-v5.5-final-icon-assets-vc10-<commit>-build.txt
```

The APK filename contains the version name, version code, and short source
commit SHA so multiple builds cannot silently overwrite one another.

Only signed release APKs are committed here. Debug APKs remain available as
short-lived GitHub Actions artifacts to avoid unnecessarily bloating repository
history.

Do not manually edit generated build files.


## GitHub Releases and tags

Every successful signed build from the current feature branch is also published
under the repository's **Releases** page.

Release tags use both Android version fields:

```text
v<version-name>-vc<version-code>
```

For example:

```text
v5.5-final-icon-assets-vc10
```

The release contains the signed APK, its SHA-256 checksum, and the build metadata
file. A tag is immutable for a source revision: if the same tag already points
to a different commit, the workflow fails and requires the application version
to be incremented rather than silently moving a published release tag.
