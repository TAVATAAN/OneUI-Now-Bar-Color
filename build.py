import subprocess
import os
import zipfile
import shutil

base = '/data/user/0/com.agy/files/antigravity/proud-brahmagupta'
android_jar = '/data/user/0/com.agy/files/android-sdk/platforms/android-34/android.jar'
if not os.path.exists(android_jar):
    android_jar = '/data/user/0/com.agy/files/.gemini/antigravity-app/brain/f2e9ea73-6caf-4717-819b-37b1ded44f9e/scratch/android.jar'

src_dir = os.path.join(base, 'src')
classes_dir = os.path.join(base, 'build', 'classes')
dex_dir = os.path.join(base, 'build', 'dex')
manifest = os.path.join(base, 'AndroidManifest.xml')
res_dir = os.path.join(base, 'res')
assets_dir = os.path.join(base, 'assets')
dex_file = os.path.join(dex_dir, 'classes.dex')
unsigned_apk = os.path.join(base, 'build', 'unsigned.apk')
aligned_apk = os.path.join(base, 'build', 'aligned.apk')
final_apk = os.path.join(base, 'build', 'NowBarColor.apk')
keystore = os.path.join(base, 'build', 'debug.keystore')

if os.path.exists(classes_dir):
    shutil.rmtree(classes_dir)
os.makedirs(classes_dir, exist_ok=True)
os.makedirs(dex_dir, exist_ok=True)

# 1. javac
sources = []
for root, dirs, files in os.walk(src_dir):
    for f in files:
        if f.endswith('.java'):
            sources.append(os.path.join(root, f))

cmd_javac = ['javac', '-cp', android_jar, '-d', classes_dir, '-source', '8', '-target', '8'] + sources
subprocess.run(cmd_javac, check=True)
print('1. javac compilation successful')

# 2. Remove compileOnly Xposed stubs from classes_dir
xposed_stubs = os.path.join(classes_dir, 'de')
if os.path.exists(xposed_stubs):
    shutil.rmtree(xposed_stubs)
    print('2. Removed Xposed stub classes from build/classes')

# 3. d8
class_files = []
for root, dirs, files in os.walk(classes_dir):
    for f in files:
        if f.endswith('.class'):
            class_files.append(os.path.join(root, f))

if os.path.exists(dex_file):
    os.remove(dex_file)

cmd_d8 = ['d8', '--lib', android_jar, '--output', dex_dir] + class_files
subprocess.run(cmd_d8, check=True)
print('3. d8 conversion successful. classes.dex generated:', os.path.exists(dex_file))

# 4. aapt package
for f in [unsigned_apk, aligned_apk, final_apk]:
    if os.path.exists(f):
        os.remove(f)

cmd_aapt = [
    'aapt', 'package', '-f',
    '-M', manifest,
    '-S', res_dir,
    '-A', assets_dir,
    '-I', android_jar,
    '-F', unsigned_apk
]
subprocess.run(cmd_aapt, check=True)
print('4. aapt package successful')

# 5. Add classes.dex and META-INF/xposed into APK
meta_xposed_dir = os.path.join(base, 'meta_xposed')
with zipfile.ZipFile(unsigned_apk, 'a') as z:
    z.write(dex_file, 'classes.dex')
    if os.path.exists(meta_xposed_dir):
        for fname in os.listdir(meta_xposed_dir):
            fpath = os.path.join(meta_xposed_dir, fname)
            z.write(fpath, f'META-INF/xposed/{fname}')
            print(f'5. Added META-INF/xposed/{fname} into APK')
print('5. Added classes.dex and META-INF/xposed into unsigned APK')

# 6. zipalign
subprocess.run(['zipalign', '-p', '-f', '4', unsigned_apk, aligned_apk], check=True)
print('6. zipalign successful')

# 7. Reuse or generate keystore & sign with apksigner
existing_ks = '/data/user/0/com.agy/files/.gemini/antigravity-app/brain/f2e9ea73-6caf-4717-819b-37b1ded44f9e/scratch/nowbar_fix/build/debug.keystore'
if not os.path.exists(keystore) and os.path.exists(existing_ks):
    shutil.copyfile(existing_ks, keystore)

if not os.path.exists(keystore):
    cmd_keytool = [
        'keytool', '-genkeypair', '-v',
        '-keystore', keystore,
        '-storepass', 'android',
        '-alias', 'androiddebugkey',
        '-keypass', 'android',
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '10000',
        '-dname', 'CN=NowBarColor,O=LSPosed,C=US'
    ]
    subprocess.run(cmd_keytool, check=True)

cmd_sign = [
    'apksigner', 'sign',
    '--ks', keystore,
    '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android',
    '--out', final_apk,
    aligned_apk
]
subprocess.run(cmd_sign, check=True)
print('7. apksigner successful. Final APK:', final_apk, 'Size:', os.path.getsize(final_apk))
