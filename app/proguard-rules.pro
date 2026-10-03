# Application-specific rules.
# Do not add rules that keep whole packages unless a dependency explicitly requires it.

# Apache Commons CSV uses this SpotBugs annotation only as compile-time metadata.
# It is not required at runtime; suppress the precise R8 missing-annotation warning.
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
