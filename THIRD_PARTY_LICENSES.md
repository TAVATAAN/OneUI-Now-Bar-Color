# Third-Party Licenses & Notices

**OneUI Now Bar Color** source code under [`src/`](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/tree/main/src) is distributed under the [MIT License](LICENSE).

Prebuilt native and Java runtime dependencies bundled under [`libs/`](https://github.com/TAVATAAN/OneUI-Now-Bar-Color/tree/main/libs) are governed by their respective upstream open-source licenses below.

---

## Bundled Third-Party Components

| Artifact Path | Component | Version | Upstream Repository | License | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `libs/jni/arm64-v8a/libdexkit.so` | **DexKit Core (Native C++ Shared Library)** | `2.3.0` | [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) | **GNU LGPL v3.0** | `0444cf15146c256e8bbd2154ae8b120130dab47e9226c7e853a7d653737a95cd` |
| `libs/runtime/dexkit.jar` | **DexKit Java / Kotlin Bridge API** | `2.3.0` | [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) | **Apache License 2.0** | `34a8c16d548aa7d8562128f4d2293cec4e0f9896bb1f46224dcd532e878900f5` |
| `libs/runtime/flatbuffers-java.jar` | **FlatBuffers Java Runtime** | `23.5.26` | [google/flatbuffers](https://github.com/google/flatbuffers) | **Apache License 2.0** | `8d10cac2ea9878896077ba437d76fdb1b9a07f55a863c560bb8a024b04103f8b` |
| `libs/runtime/kotlin-stdlib.jar` | **Kotlin Standard Library** | `1.5.0` | [JetBrains/kotlin](https://github.com/JetBrains/kotlin) | **Apache License 2.0** | `52283996fe4067cd7330288b96ae67ecd463614dc741172c54d9d349ab6a9cd` |

---

## LGPL-3.0 Compliance Notice (`libdexkit.so`)

- `libdexkit.so` (artifact `org.luckypray:dexkit:2.3.0`, native ARM64 core) is copyrighted by **LuckyPray** and licensed under the **GNU Lesser General Public License, Version 3.0 (LGPL-3.0)**.
- **OneUI Now Bar Color** links to `libdexkit.so` dynamically as a standalone shared library via JNI (`System.loadLibrary("dexkit")`). Users may replace or relink `libdexkit.so` inside the unpacked APK independently of the MIT-licensed module code.
- **Corresponding Source Code:** The complete C++ source code for DexKit Core (`libdexkit.so`) is publicly available upstream at:
  - **Repository:** https://github.com/LuckyPray/DexKit
  - **Core C++ Sources:** https://github.com/LuckyPray/DexKit/tree/master/Core
  - **Maven Artifact:** `org.luckypray:dexkit:2.3.0`

---

## Full License Texts

Verbatim copies of the applicable third-party licenses are included in [`LICENSES/`](LICENSES):

- **GNU Lesser General Public License v3.0 (LGPL-3.0):** [`LICENSES/LGPL-3.0.txt`](LICENSES/LGPL-3.0.txt)
- **GNU General Public License v3.0 (GPL-3.0, incorporated by LGPL-3.0):** [`LICENSES/GPL-3.0.txt`](LICENSES/GPL-3.0.txt)
- **Apache License, Version 2.0:** [`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt)
