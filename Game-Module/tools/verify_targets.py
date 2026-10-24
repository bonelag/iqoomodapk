"""Read original DEX declarations, not JADX aliases. Offline only, standard library."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]

class Dex:
    def __init__(self, data):
        self.d = data
        assert data.startswith(b'dex\n'), 'Not DEX'
        count, offset = self.u32(56), self.u32(60)
        self.strings = []
        for i in range(count):
            at = self.u32(offset + 4*i)
            _, at = self.uleb(at)
            end = data.index(0, at)
            self.strings.append(data[at:end].decode('utf-8', errors='replace'))
        self.types = [self.strings[self.u32(self.u32(68)+4*i)] for i in range(self.u32(64))]
        self.protos = []
        for i in range(self.u32(72)):
            at = self.u32(76)+12*i
            params = self.u32(at+8)
            args = '' if not params else ''.join(self.types[self.u16(params+4+2*j)] for j in range(self.u32(params)))
            self.protos.append('('+args+')'+self.types[self.u32(at+4)])
        self.method_ids = []
        for i in range(self.u32(88)):
            at = self.u32(92)+8*i
            self.method_ids.append((self.types[self.u16(at)], self.strings[self.u32(at+4)], self.protos[self.u16(at+2)]))
        self.field_ids = []
        for i in range(self.u32(80)):
            at = self.u32(84)+8*i
            self.field_ids.append((self.types[self.u16(at)], self.strings[self.u32(at+4)], self.types[self.u16(at+2)]))
        self.methods, self.fields, self.supers = {}, {}, {}
        for i in range(self.u32(96)):
            at = self.u32(100)+32*i
            parent = self.u32(at+8)
            self.supers[self.types[self.u32(at)]] = self.types[parent] if parent != 0xffffffff else None
            cursor = self.u32(at+24)
            if not cursor: continue
            counts = []
            for _ in range(4):
                n, cursor = self.uleb(cursor); counts.append(n)
            for n in counts[:2]:
                idx = 0
                for _ in range(n):
                    delta, cursor = self.uleb(cursor); idx += delta
                    flags, cursor = self.uleb(cursor)
                    self.fields[self.field_ids[idx]] = flags
            for n in counts[2:]:
                idx = 0
                for _ in range(n):
                    delta, cursor = self.uleb(cursor); idx += delta
                    flags, cursor = self.uleb(cursor)
                    _, cursor = self.uleb(cursor)
                    self.methods[self.method_ids[idx]] = flags
    def u16(self, at): return struct.unpack_from('<H', self.d, at)[0]
    def u32(self, at): return struct.unpack_from('<I', self.d, at)[0]
    def uleb(self, at):
        value, shift = 0, 0
        while True:
            n = self.d[at]; at += 1
            value |= (n & 127) << shift
            if not n & 128: return value, at
            shift += 7
            if shift > 35: raise ValueError('invalid ULEB128')

def descriptor(name): return 'L'+name.replace('.', '/')+';'

class Artifact:
    def __init__(self, path):
        self.path = path
        self.sha = hashlib.sha256(path.read_bytes()).hexdigest()
        self.methods, self.fields, self.supers = {}, {}, {}
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.startswith('classes') and n.endswith('.dex'):
                    d = Dex(z.read(n))
                    self.methods.update(d.methods); self.fields.update(d.fields); self.supers.update(d.supers)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apks', type=Path, default=ROOT.parent/'investigation_game_20261021/apks')
    parser.add_argument('--services', type=Path, default=ROOT/'research/vivo-services.jar')
    args = parser.parse_args()
    cube = Artifact(args.apks/'com.vivo.gamecube/base.apk')
    watch = Artifact(args.apks/'com.vivo.gamewatch/GameWatch.apk')
    framework = Artifact(args.services)
    checks = []
    def method(artifact, cls, name, proto, static=None):
        key = (descriptor(cls), name, proto)
        flags = artifact.methods.get(key)
        good = flags is not None and (static is None or bool(flags & 8) == static)
        checks.append({'kind':'method', 'class':cls, 'name':name, 'prototype':proto, 'passed':good})
    def field(artifact, cls, name, typ, static=None):
        flags = artifact.fields.get((descriptor(cls), name, typ))
        good = flags is not None and (static is None or bool(flags & 8) == static)
        checks.append({'kind':'field', 'class':cls, 'name':name, 'type':typ, 'passed':good})
    S, C = 'Ljava/lang/String;', 'Landroid/content/Context;'
    F = descriptor('com.vivo.common.supportlist.pojo.ConfiguredFunction')
    Q = descriptor('com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.model.QuickSwitchItemType')
    I = descriptor('com.vivo.gameassistant.frameinterpolation.FrameInterpolationState')
    for name in ['d0','F0']: method(cube,'com.vivo.gameassistant.utils.f0', name, '('+F+S+')Z', True)
    method(cube,'com.vivo.gameassistant.utils.f0','G0','('+Q+S+')Z',True)
    method(cube,'com.vivo.gameassistant.utils.f0','k0','('+S+')Z',True)
    method(cube,'com.vivo.gameassistant.utils.f0','S0','('+C+S+')Z',True)
    method(cube,'com.vivo.gameassistant.superresolution.i','K0','('+S+'Z)V')
    sr = 'com.vivo.gameassistant.superresolution.i'
    method(cube,sr,'l1','(I)Z')
    method(cube,sr,'t1','(IZ)V')
    method(cube,sr,'w1','('+C+S+'ZZ)Z')
    method(cube,sr,'v1','('+C+S+'ZZ)Z')
    method(cube,sr,'a1','(I)Z')
    method(cube,sr,'J1','(I'+S+')V')
    method(cube,sr,'n1','(I)V')
    for name in ['P0','O0']: method(cube,sr,name,'()V')
    field(cube,sr,'a',C)
    field(cube,sr,'k',S)
    field(cube,sr,'C','D')
    for name in ['b','l']: field(cube,sr,name,'Z')
    field(cube,sr,'z',descriptor('com.vivo.gameassistant.superresolution.a'))
    panel = 'com.vivo.gameassistant.homegui.sideslide.panels.qchip.CommonSuperResolutionFrameView'
    field(cube,sr,'q',descriptor(panel))
    method(cube,panel,'c','('+C+')V')
    method(cube,panel,'g','(I)V')
    field(cube,panel,'a','I')
    field(cube,panel,'f','Landroid/widget/LinearLayout;')
    method(cube,'com.vivo.gameassistant.superresolution.o','<init>','()V')
    method(cube,'com.vivo.gameassistant.superresolution.a','p','(Ln8/e;)V')
    prefs = 'com.vivo.common.utils.f0'
    method(cube,prefs,'h','()'+descriptor(prefs),True)
    for name in ['e','f']: method(cube,prefs,name,'('+C+S+')I')
    method(cube,prefs,'m','('+C+S+'I)Z')
    method(cube,prefs,'l','('+C+S+'I)Z')
    method(cube,'com.vivo.gameassistant.utils.d0','a','()Lcom/vivo/gameassistant/utils/d0;',True)
    method(cube,'com.vivo.gameassistant.utils.d0','b','(Ljava/lang/Object;Z)V')
    method(cube,'com.vivo.common.algorithem.b','f','('+C+S+S+')Z',True)
    method(cube,'com.vivo.gameassistant.o','x0','()'+descriptor(sr))
    method(cube,'com.vivo.gameassistant.superresolution.m','f','('+S+')Z')
    method(cube,'com.vivo.common.utils.f0','j','('+C+S+')Z')
    method(cube,'com.vivo.gameassistant.frameinterpolation.h','a1','()Z')
    method(cube,'com.vivo.gameassistant.frameinterpolation.h','f0','('+I+S+')I')
    method(cube,'com.vivo.gameassistant.frameinterpolation.h','J0','()V')
    field(cube,'com.vivo.gameassistant.frameinterpolation.h','F','I')
    method(cube,'l9.a','c','()I')
    method(cube,'l9.a','l','(I)V')
    field(cube,'l9.a','e','Ljava/lang/Float;')
    method(cube,'l9.b','f','(Z)Ljava/lang/Float;')
    method(cube,'l9.b','g','()Z')
    method(cube,'m9.j','s','('+S+')'+descriptor('com.vivo.common.frameintersupportlist.entity.a'))
    method(cube,'m9.j','<init>','()V')
    for name in ['k','l']: method(cube,'com.vivo.gameassistant.frameinterpolation.k',name,'('+S+')Lq8/b;',True)
    method(cube,'m9.d','l','(IIII'+I+')V')
    method(cube,'com.vivo.common.utils.j0','b','('+C+'ZIIII)V',True)
    method(cube,'com.vivo.gameassistant.frameinterpolation.i','e','()Lcom/vivo/gameassistant/frameinterpolation/i;',True)
    method(cube,'com.vivo.gameassistant.frameinterpolation.i','h','(IIII)V')
    method(cube,'com.vivo.gameassistant.o','V','()Lcom/vivo/gameassistant/o;',True)
    method(cube,'com.vivo.gameassistant.o','O0','()'+S)
    method(cube,'q8.b','<init>','()V')
    method(cube,'q8.b','l','(I)V'); method(cube,'q8.b','m','(Z)V')
    method(cube,'s7.b','<init>','(III)V')
    method(cube,'com.vivo.common.frameintersupportlist.entity.a','<init>', '('+S*3+'IZLjava/util/List;Ljava/util/List;Ljava/util/Map;DDII)V')
    method(cube,'com.vivo.common.frameintersupportlist.entity.a','k','()'+S)
    method(cube,'n8.e','<init>','('+S*3+'IIIIIDDDDIIII'+S+')V')
    method(cube,'n8.e','p','()'+S)
    for name in ['h','i']: field(cube,'com.vivo.gameassistant.AssistantUIService',name,'Ljava/util/List;',True)
    for cls in [F,Q,I]: checks.append({'kind':'enum','class':cls,'passed':cube.supers.get(cls)=='Ljava/lang/Enum;'})
    method(watch,'e0.a','o','(I[I)I')
    service = 'com.vivo.services.game.GameManagerService'
    method(framework,service,'systemReady','()V')
    field(framework,service,'mContext',C,False)
    for name in ['dispatchMEMCEnabled','dispatchVSREnabled','dispatchVAAEnabled']:
        method(framework,service,name,'('+S+'IIII)V')
    for path, stem in [('memc','MEMC'),('sr','VSR'),('vaa','VAA')]:
        base = 'com.vivo.services.game.'+path+'.'
        for api in ['Gles','Vulkan']:
            method(framework,base+stem+'Controller','is'+stem+api+'WhitelistApp','('+S+')Z')
            field(framework,base+stem+'Controller','SUPPORT_'+stem+'_'+api.upper(),'Z',True)
            method(framework,base+stem+'ConfigParser','support'+stem+api+'ForCurrentModel','('+S+')Z',True)
            method(framework,base+stem+'ConfigParser','support'+stem+api,'()Z',True)
    memc = 'com.vivo.services.game.memc.'
    method(framework,memc+'MEMCController','getSdkGameTargetFps','('+S+'I)I')
    method(framework,memc+'MEMCController','updateNoErrorState','()V')
    field(framework,memc+'MEMCController','mCurMEMCPackage',S)
    field(framework,memc+'MEMCController','mCurMEMCPid','I')
    field(framework,memc+'MEMCController','mMEMCGlesRunning','I')
    field(framework,memc+'MEMCController','mMEMCVulkanRunning','I')
    field(framework,memc+'MEMCController','mNoError','Z')
    field(framework,memc+'MEMCController','mValues','[I')
    method(framework,memc+'MEMCConfigParser','getMEMCFpsForPackage','('+S+'IIDZ)Lcom/vivo/services/game/memc/MEMCConfigParser$MEMCFps;',True)
    method(framework,memc+'MEMCConfigParser$MEMCFps','<init>','(II)V')
    field(framework,memc+'MEMCConfigParser','MEMC_PACKAGE_FPS_CONFIGS','Ljava/util/HashMap;',True)
    result = {'checks':checks, 'passed':sum(c['passed'] for c in checks), 'total':len(checks),
              'artifacts':[{'path':str(a.path),'sha256':a.sha} for a in [cube,watch,framework]],
              'scope':'Declared DEX method/field signatures and enum parents. Not runtime hook execution.'}
    output = ROOT/'dist/target-verification.json'
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps(result,indent=2)+'\n')
    for c in checks:
        if not c['passed']: print('MISSING:', c)
    print('Target DEX verification: %d/%d passed' % (result['passed'], result['total']))
    if result['passed'] != result['total']: raise SystemExit(1)

if __name__ == '__main__': main()
