package de.robv.android.xposed;

import java.io.File;

/** Compile-time stub; real implementation is provided at runtime by LSPosed. */
public class XSharedPreferences {
    public XSharedPreferences(String packageName, String prefFileName) {}

    public File getFile() {
        return new File("");
    }

    public void reload() {}

    public String getString(String key, String defValue) {
        return defValue;
    }
}
