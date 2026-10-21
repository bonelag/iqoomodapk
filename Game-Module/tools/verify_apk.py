"""Validate the packaged module without installing or running it."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
from verify_targets import Dex

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', nargs='?', type=Path, default=ROOT/'dist/VivoGameUnlock-0.1.3.apk')
    args = parser.parse_args()
    checks = []
    def check(value, name):
        checks.append({'check':name, 'passed':bool(value)})
    with zipfile.ZipFile(args.apk) as z:
        files = set(z.namelist())
        check({'classes.dex','AndroidManifest.xml','resources.arsc','assets/xposed_init'} <= files, 'required APK entries')
        entry = z.read('assets/xposed_init').decode().strip()
        check(entry == 'io.github.vivogameunlock.GameUnlock', 'legacy Xposed entry point')
        dexes = [Dex(z.read(n)) for n in files if n.startswith('classes') and n.endswith('.dex')]
        classes = {k for d in dexes for k in d.supers}
        fields = {k:v for d in dexes for k,v in d.fields.items()}
        for name, typ in [('loader','Ljava/lang/ClassLoader;'),('process','Ljava/lang/String;')]:
            flags = fields.get(('Lio/github/vivogameunlock/GameUnlock;',name,typ),0)
            check(flags & 16, 'hook session '+name+' is immutable across package callbacks')
        check('Lio/github/vivogameunlock/GameUnlock;' in classes, 'hook entry class present')
        check('Lio/github/vivogameunlock/MainActivity;' in classes, 'configuration activity present')
        check('Lio/github/vivogameunlock/ConfigProvider;' in classes, 'configuration provider present')
        check(not any(c.startswith('Lde/robv/android/xposed/') for c in classes), 'Xposed API not bundled')
        check(not any(n.endswith(('.jar','.so','.sh','.jks','.keystore')) for n in files), 'no native/shell/key payloads')
        forbidden = {
            ('Ljava/lang/Runtime;','exec'), ('Ljava/lang/ProcessBuilder;','<init>'),
            ('Landroid/os/PowerManager;','reboot'), ('Landroid/app/ActivityManager;','forceStopPackage'),
            ('Landroid/content/pm/PackageInstaller;','createSession'),
        }
        refs = {(c,n) for d in dexes for c,n,p in d.method_ids}
        check(not forbidden.intersection(refs), 'no shell/reboot/force-stop/install API references')
        mutations = [r for r in refs if r[0].startswith('Landroid/provider/Settings$') and r[1].startswith('put')]
        check(not mutations, 'no direct persistent system Settings writes')
        check(not any(c.startswith(('Ljava/net/','Lokhttp3/')) for c,n in refs), 'no network client API references')
    report = {'artifact':args.apk.name, 'sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'checks':checks, 'passed':sum(c['passed'] for c in checks), 'total':len(checks), 'runtime_tested':False}
    (ROOT/'dist/apk-verification.json').write_text(json.dumps(report,indent=2)+'\n')
    for c in checks:
        if not c['passed']: print('FAILED:', c['check'])
    print('APK verification: %d/%d passed' % (report['passed'],report['total']))
    if report['passed'] != report['total']: raise SystemExit(1)

if __name__ == '__main__': main()
