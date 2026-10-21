"""Offline SDK build. No adb, shell scripts, device connection, or dependency downloads."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def run(args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)

def main():
    manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    version = manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', 'D:/Code/REMOTE_DESKTOP/android')))
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', 'D:/Code/REMOTE_DESKTOP/android/jdk17')))
    args = parser.parse_args()
    sdk, jdk = args.sdk.resolve(), args.java_home.resolve()
    bt = sdk / 'build-tools/35.0.1'
    android = sdk / 'platforms/android-35/android.jar'
    api = ROOT / 'vendor/xposed-api-82.jar'
    if not api.exists():
        candidates = list((Path.home() / '.gradle/caches/modules-2/files-2.1/de.robv.android.xposed/api/82').glob('*/api-82.jar'))
        if len(candidates) != 1:
            raise SystemExit('Supply vendor/xposed-api-82.jar from https://api.xposed.info/ (compile-only).')
        shutil.copyfile(candidates[0], api)
    api_hash = hashlib.sha256(api.read_bytes()).hexdigest()
    # Record the local artifact identity; vendor/SHA256SUMS is the reviewable trust anchor.
    trust = ROOT / 'vendor/SHA256SUMS'
    if trust.exists() and trust.read_text().split()[0] != api_hash:
        raise SystemExit('Xposed API digest differs from vendor/SHA256SUMS')
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    work = ROOT / 'build' / stamp
    classes, dex, testclasses = work/'classes', work/'dex', work/'tests'
    for d in (classes, dex, testclasses, ROOT/'dist', ROOT/'.local'): d.mkdir(parents=True, exist_ok=True)
    java, javac = jdk/'bin/java.exe', jdk/'bin/javac.exe'
    sources = sorted((ROOT/'app/src/main/java').rglob('*.java'))
    # Argument files preserve Unicode and spaces without shell interpolation.
    source_list = work/'sources.args'
    source_list.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf-8')
    run([javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:deprecation',
         '-classpath', str(android)+os.pathsep+str(api), '-d', classes, '@'+str(source_list)])
    run([javac, '-encoding', 'UTF-8', '-d', testclasses,
         ROOT/'app/src/main/java/io/github/vivogameunlock/Policy.java', ROOT/'tests/PolicyTest.java',
         ROOT/'app/src/main/java/io/github/vivogameunlock/ConfigContract.java', ROOT/'tests/ConfigContractTest.java'])
    run([java, '-cp', testclasses, 'PolicyTest'])
    run([java, '-cp', testclasses, 'ConfigContractTest'])
    jar = work/'module.jar'
    with zipfile.ZipFile(jar, 'w') as z:
        for p in classes.rglob('*.class'): z.write(p, p.relative_to(classes).as_posix())
    run([java, '-cp', bt/'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28',
         '--lib', android, '--classpath', api, '--output', dex, jar])
    resources = work/'resources.zip'
    run([bt/'aapt2.exe', 'compile', '--dir', ROOT/'app/src/main/res', '-o', resources])
    unsigned = work/'unsigned.apk'
    run([bt/'aapt2.exe', 'link', '-o', unsigned, '--manifest', ROOT/'app/src/main/AndroidManifest.xml',
         '-I', android, resources])
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as z:
        for p in dex.glob('*.dex'): z.write(p, p.name)
        for p in (ROOT/'app/src/main/assets').rglob('*'):
            if p.is_file(): z.write(p, 'assets/'+p.relative_to(ROOT/'app/src/main/assets').as_posix())
    aligned = work/'aligned.apk'
    run([bt/'zipalign.exe', '-p', '-f', '4', unsigned, aligned])
    key = ROOT/'.local/development.jks'
    if not key.exists():
        run([jdk/'bin/keytool.exe', '-genkeypair', '-keystore', key, '-storepass', 'android',
             '-keypass', 'android', '-alias', 'development', '-keyalg', 'RSA', '-keysize', '3072',
             '-validity', '10000', '-dname', 'CN=Vivo Game Unlock Development'])
    apk = ROOT/('dist/VivoGameUnlock-'+version+'.apk')
    run([java, '-jar', bt/'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', 'development',
         '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', apk, aligned])
    run([java, '-jar', bt/'lib/apksigner.jar', 'verify', '--verbose', apk])
    run([bt/'zipalign.exe', '-c', '4', apk])
    run([sys.executable, ROOT/'tools/verify_apk.py', apk])
    target_report = None
    if (ROOT/'research/vivo-services.jar').exists() and (ROOT.parent/'investigation_game_20261021/apks/com.vivo.gamecube/base.apk').exists():
        run([sys.executable, ROOT/'tools/verify_targets.py'])
        target_report = 'target-verification.json'
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    (ROOT/'dist/SHA256SUMS').write_text(sha+'  '+apk.name+'\n')
    result = {'artifact': apk.name, 'sha256': sha, 'xposed_api_sha256': api_hash,
              'sdk': str(android), 'device_actions': [], 'runtime_tested': False,
              'target_declarations': target_report,
              'validation': ['javac', 'PolicyTest', 'ConfigContractTest', 'D8', 'aapt2', 'apksigner verify', 'zipalign check', 'verify_apk']}
    (ROOT/'dist/build-report.json').write_text(json.dumps(result, indent=2)+'\n')
    # A reviewable source bundle excludes firmware, decompiled sources, signing keys and build intermediates.
    with zipfile.ZipFile(ROOT/('dist/VivoGameUnlock-'+version+'-source.zip'), 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for prefix in ['app', 'tools', 'tests', 'vendor']:
            for p in (ROOT/prefix).rglob('*'):
                if p.is_file() and '__pycache__' not in p.parts: z.write(p, p.relative_to(ROOT).as_posix())
        for name in ['README.md','build.ps1','.gitignore','research/HOOKS.md','research/FIX-0.1.1.md','research/FIX-0.1.2.md','research/FIX-0.1.3.md']:
            z.write(ROOT/name, name)
    print('APK: '+str(apk))
    print('SHA256: '+sha)

if __name__ == '__main__': main()
