#!/usr/bin/env python3
"""Destructive ONLY to a disposable emulator: old UI save → signed install-r → preserve all fields."""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--old-apk',required=True);p.add_argument('--new-apk',required=True);p.add_argument('--output',required=True);p.add_argument('--navigation-only',action='store_true');a=p.parse_args()
out=Path(a.output);out.mkdir(parents=True,exist_ok=True);pkg='com.goldenunicorn.troopmanager'
def adb(*args,timeout=45):
 r=subprocess.run([a.adb,*args],capture_output=True,timeout=timeout)
 if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
 return r.stdout

def dump():
 adb('shell','uiautomator','dump','/sdcard/realm-window.xml',timeout=40)
 return ET.fromstring(adb('shell','cat','/sdcard/realm-window.xml'))
def tap_node(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def find(label,seconds=100):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  try:
   tree=dump()
   for n in tree.iter('node'):
    if n.get('text')==label or n.get('content-desc')==label:return n
   for n in tree.iter('node'):
    if n.get('text')=='Wait':tap_node(n)
  except (RuntimeError,subprocess.TimeoutExpired,ET.ParseError):pass
  time.sleep(2)
 raise RuntimeError('UI target missing: '+label)
def click(label):tap_node(find(label));print('UI:',label,flush=True)
def prefs():
 raw=adb('shell','run-as',pkg,'cat','shared_prefs/realm_save.xml').decode()
 root=ET.fromstring(raw);return {n.get('name'):n.text for n in root.findall('string')}
def preserve(before,after,path='state'):
 if isinstance(before,dict):
  for k,v in before.items():
   if path=='state' and k=='version':continue
   assert k in after,(path,k)
   preserve(v,after[k],path+'.'+k)
 elif isinstance(before,list):
  assert len(before)==len(after),path
  for i,(x,y) in enumerate(zip(before,after)):preserve(x,y,path+'['+str(i)+']')
 else:assert before==after,(path,before,after)

def navigation_smoke():
 for label,anchor in [('Armee','ARMEE'),('Welt','WELT & MISSIONEN'),('Hof','HOF & CHARAKTERE'),('Stadt','DEINE STADT')]:
  click(label);find(anchor,seconds=90)
  # An accessibility title proves the route changed before saving the frame.
  time.sleep(5)
  (out/('v045-'+label.lower()+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 print('PASS: all four new page titles visible',flush=True)

assert adb('shell','getprop','ro.kernel.qemu').strip()==b'1','Requires disposable emulator'
if a.navigation_only:
 navigation_smoke();raise SystemExit(0)
print('Install baseline',flush=True);print(adb('install','-r',a.old_apk,timeout=420).decode(),flush=True)
adb('shell','am','start','-n',pkg+'/.MainActivity')
click('Neues Reich')
# Character creation scrolls; keep default identity/origin, generate save using actual old APK UI.
for i in range(4):
 try:node=find('Reich gründen',seconds=15);break
 except RuntimeError:adb('shell','input','swipe','240','700','240','250','300')
else:raise RuntimeError('Character creation button unavailable')
tap_node(node);print('UI: Reich gründen',flush=True)
try:click('Überspringen')
except RuntimeError:pass
beforePrefs=prefs();before=json.loads(beforePrefs['game_state_v1']);assert before['version']==2
(out/'v04-game-state.json').write_text(json.dumps(before,ensure_ascii=False,indent=2));(out/'v04-prefs.xml').write_bytes(adb('shell','run-as',pkg,'cat','shared_prefs/realm_save.xml'))
adb('shell','am','force-stop',pkg)
print('Install update -r',flush=True);print(adb('install','-r',a.new_apk,timeout=420).decode(),flush=True)
adb('shell','am','start','-n',pkg+'/.MainActivity');click('Spiel fortsetzen')
find('Reich')
afterPrefs=prefs();after=json.loads(afterPrefs['game_state_v1']);assert after['version']==3
preserve(before,after);assert afterPrefs['migration_backup_v2']==beforePrefs['game_state_v1']
(out/'v045-game-state.json').write_text(json.dumps(after,ensure_ascii=False,indent=2))
(out/'v045-realm.png').write_bytes(adb('exec-out','screencap','-p'))
navigation_smoke()
(out/'result.json').write_text(json.dumps({'pass':True,'baselineVersion':2,'updateVersion':3,'allOriginalFieldsPreserved':True,'protectedBackupMatches':True,'tabs':['Reich','Stadt','Armee','Welt','Hof']},indent=2));print('PASS: signed update, preservation, migration backup, navigation',flush=True)
