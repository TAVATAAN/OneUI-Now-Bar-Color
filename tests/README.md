# Host regression tests

`python3 tests/run_host_tests.py --android-jar /path/to/android.jar`

Compiles production Java against the provided Android API, then runs the actual
scheduler, reflection cache and style helper on lightweight host doubles. These
checks cover coalescing, detach/reattach, drawable reuse, native replacement,
gradient restoration and negative reflection lookups. They do **not** execute
Samsung code, LSPosed, Android rendering or the GPU and are not an FPS benchmark.

The doubles live in a temporary directory and are never packaged in the APK.

Media regressions also exercise late collapse/layer changes without a hook,
zero-alpha stack layers, removed native backgrounds and unchanged-frame work.
Use `--source-root /path/to/checkout` to run the same suite against another version.

Gradient regression checks include mutations of the same drawable (alpha, tint,
filter, visibility and solid-color replacement), palette opacity and native tint
restoration. Drawable doubles now model setColor clearing gradient colors.
