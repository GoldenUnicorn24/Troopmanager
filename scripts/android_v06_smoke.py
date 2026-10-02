#!/usr/bin/env python3
"""Bounded v0.6 UI probes. Operates on an explicitly selected disposable emulator.
No package-data clearing or uninstall. Run one action at a time; evidence is JSON/XML/PNG.
"""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', required=True)
parser.add_argument('action', choices=['launch', 'create', 'tap', 'scroll', 'capture', 'day', 'restart', 'verify', 'update'])
parser.add_argument('--label', default='')
parser.add_argument('--apk')
a = parser.parse_args()
out = Path(a.output)
out.mkdir(parents=True, exist_ok=True)
pkg = 'com.goldenunicorn.troopmanager'

def adb(*args, timeout=40):
    r = subprocess.run([a.adb, '-s', a.serial, *args], capture_output=True, timeout=timeout)
    if r.returncode:
        raise RuntimeError(r.stderr.decode(errors='replace'))
    return r.stdout

assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Use a disposable emulator'

def dump():
    adb('shell', 'rm', '-f', '/sdcard/v06-window.xml')
    result = adb('shell', 'uiautomator', 'dump', '/sdcard/v06-window.xml', timeout=20)
    assert b'ERROR:' not in result, result.decode(errors='replace')
    raw = adb('shell', 'cat', '/sdcard/v06-window.xml')
    return raw, ET.fromstring(raw)

def tap(label):
    raw, tree = dump()
    candidates = [n for n in tree.iter('node') if n.get('text') == label or n.get('content-desc') == label]
    assert candidates, 'Missing UI label: ' + label
    n = candidates[-1]
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    assert y2 > y1 and x2 > x1, 'Empty target bounds'
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def state():
    root = ET.fromstring(adb('shell', 'run-as', pkg, 'cat', 'shared_prefs/realm_save.xml'))
    values = {n.get('name'): n.text for n in root.findall('string')}
    return json.loads(values['game_state_v1'])

def capture(name):
    raw, tree = dump()
    (out / (name + '.xml')).write_bytes(raw)
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    print(json.dumps([n.get('text') or n.get('content-desc') for n in tree.iter('node') if n.get('text') or n.get('content-desc')], ensure_ascii=False))

result = {'action': a.action, 'serial': a.serial, 'timeUtc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
if a.action == 'launch':
    adb('shell', 'am', 'force-stop', pkg)
    result['amStart'] = adb('shell', 'am', 'start', '-W', '-n', pkg+'/.MainActivity').decode()
elif a.action == 'create':
    tap('Reich gründen')
elif a.action == 'tap':
    tap(a.label)
elif a.action == 'scroll':
    adb('shell', 'input', 'swipe', '520', '1450', '520', '450', '350')
elif a.action == 'capture':
    capture(a.label or 'screen')
elif a.action == 'day':
    before = state()
    tap('Tag '+str(before['day'])+' →')
    result['beforeDay'] = before['day']
elif a.action in ('restart', 'update'):
    before = state()
    adb('shell', 'am', 'force-stop', pkg)
    if a.action == 'update':
        assert a.apk, '--apk required'
        result['install'] = adb('install', '-r', a.apk, timeout=50).decode()
    result['amStart'] = adb('shell', 'am', 'start', '-W', '-n', pkg+'/.MainActivity').decode()
    after = state()
    assert before == after, 'Stored campaign changed during restart/update'
    result['campaignPreserved'] = True
elif a.action == 'verify':
    s = state()
    assert s['version'] == 4
    assert s['world']['initialized']
    assert len({x['id'] for x in s['world']['armies']}) == len(s['world']['armies'])
    result.update(day=s['day'], version=s['version'], armyPools=len(s['armyPools']), factions=len(s['world']['factions']), checksum=bool(s.get('_checksum')))
    (out / 'campaign.json').write_text(json.dumps(s, ensure_ascii=False, indent=2))
if 'amStart' in result:
    result['launchStatusOk'] = 'Status: ok' in result['amStart']
result['pass'] = True
(out / (a.action + '-' + (re.sub('[^a-zA-Z0-9_-]', '-', a.label) or 'result') + '.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2))
print(json.dumps(result, ensure_ascii=False), flush=True)
