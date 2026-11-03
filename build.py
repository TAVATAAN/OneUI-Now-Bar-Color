#!/usr/bin/env python3
"""Build from any checkout. --check-java requires only a JDK and Android jar."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

BASE = Path(__file__).resolve().parent


def run(command):
    subprocess.run([str(arg) for arg in command], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar', default=os.environ.get('ANDROID_JAR'))
    parser.add_argument('--build-tools', default=os.environ.get('ANDROID_BUILD_TOOLS'))
    parser.add_argument('--check-java', action='store_true')
    parser.add_argument('--keystore', default=str(BASE / 'build' / 'debug.keystore'))
    parser.add_argument('--alias', default='androiddebugkey')
    args = parser.parse_args()
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    android = Path(args.android_jar) if args.android_jar else None
    if android is None and sdk:
        platforms = list((Path(sdk) / 'platforms').glob('android-*/android.jar'))
        platforms = [p for p in platforms if p.parent.name[8:].isdigit()]
        if platforms:
            android = max(platforms, key=lambda p: int(p.parent.name[8:]))
    if android is None:
        default_sdk = Path('/data/user/0/com.agy/files/android-sdk/platforms')
        if default_sdk.is_dir():
            platforms = list(default_sdk.glob('android-*/android.jar'))
            platforms = [p for p in platforms if p.parent.name[8:].isdigit()]
            if platforms:
                android = max(platforms, key=lambda p: int(p.parent.name[8:]))
    if android is None or not android.is_file():
        parser.error('Supply --android-jar /path/to/android.jar, or set ANDROID_HOME / ANDROID_SDK_ROOT.')
    if not args.check_java and not (BASE / 'res/drawable/ic_launcher.png').is_file():
        parser.error('Missing res/drawable/ic_launcher.png.')

    build = BASE / 'build'
    classes = build / 'classes'
    dex = build / 'dex'
    for directory in (classes, dex):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    sources = sorted((BASE / 'src').rglob('*.java'))
    # javac also works via the JDK compiler module on hosts lacking a javac launcher.
    javac = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    run(javac + ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', android, '-d', classes] + sources)
    print('Java compilation passed.', flush=True)
    if args.check_java:
        return

    def tool(name):
        candidate = Path(args.build_tools) / name if args.build_tools else None
        if candidate is not None and candidate.is_file():
            return candidate
        resolved = shutil.which(name)
        if not resolved:
            raise RuntimeError('Missing ' + name + '; set --build-tools or PATH.')
        return resolved

    stubs = build / 'xposed-compile-only.jar'
    with zipfile.ZipFile(stubs, 'w') as archive:
        for path in sorted((classes / 'de').rglob('*.class')):
            archive.write(path, path.relative_to(classes))
    app_classes = sorted((classes / 'io').rglob('*.class'))
    run([tool('d8'), '--min-api', '31', '--lib', android, '--classpath', stubs,
         '--output', dex] + app_classes)
    unsigned = build / 'unsigned.apk'
    aligned = build / 'aligned.apk'
    final = build / 'NowBarColor.apk'
    run([tool('aapt'), 'package', '-f', '-M', BASE / 'AndroidManifest.xml',
         '-S', BASE / 'res', '-A', BASE / 'assets', '-I', android, '-F', unsigned])
    with zipfile.ZipFile(unsigned, 'a') as archive:
        for path in sorted(dex.glob('*.dex')):
            archive.write(path, path.name)
        for path in sorted((BASE / 'meta_xposed').iterdir()):
            if path.is_file():
                archive.write(path, 'META-INF/xposed/' + path.name)
    run([tool('zipalign'), '-p', '-f', '4', unsigned, aligned])
    keystore = Path(args.keystore).expanduser().resolve()
    signing_env = os.environ.copy()
    signing_env.setdefault('NOWBAR_STORE_PASS', 'android')
    signing_env.setdefault('NOWBAR_KEY_PASS', signing_env['NOWBAR_STORE_PASS'])
    if not keystore.exists():
        if keystore != (build / 'debug.keystore').resolve():
            raise RuntimeError('Provided signing keystore does not exist: ' + str(keystore))
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(keystore),
                        '-storepass:env', 'NOWBAR_STORE_PASS', '-keypass:env', 'NOWBAR_KEY_PASS',
                        '-alias', args.alias, '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
                        '-dname', 'CN=NowBarColor,O=LSPosed,C=US'], env=signing_env, check=True)
    subprocess.run([str(tool('apksigner')), 'sign', '--ks', str(keystore), '--ks-key-alias', args.alias,
                    '--ks-pass', 'env:NOWBAR_STORE_PASS', '--key-pass', 'env:NOWBAR_KEY_PASS',
                    '--out', str(final), str(aligned)], env=signing_env, check=True)
    run([tool('apksigner'), 'verify', '--verbose', final])
    print('APK:', final)


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print('Build failed:', error, file=sys.stderr)
        sys.exit(1)
