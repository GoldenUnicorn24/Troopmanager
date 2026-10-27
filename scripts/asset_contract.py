"""Single production/checking contract; no generated guides are installed as production art."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
STYLE = ('Photorealistic medieval fantasy, real skin pores and material microtexture, adult anatomy and natural proportions, '
         'no painting, brushstrokes, illustration or cartoon; soft directional light from upper left, '
         'camera-like light and physically realistic materials, front view, '
         'no lettering, watermark or frame. Isolate exactly the requested layer; all other pixels transparent. ')
POSES = ('standing', 'sitting', 'walk_0', 'walk_1', 'walk_2', 'walk_3')
SCENES = {'tavern': 2.2, 'market': 2.4, 'barracks': 2.2, 'castle': 2.6}
SCENE_FOREGROUND_ROWS = {'tavern': {1,2,3,4,5,6,7}, 'market': {1,2,5,6,7},
                         'barracks': {1,3,6,7}, 'castle': {1,2,4,5,6}}

def catalog():
    result = []
    def add(path, width, height, alpha=True, tint=False, detail='', anchor='eyes', allow_empty=False):
        registration = {'centerX': width // 2, 'eyeY': 560} if anchor == 'eyes' else {'centerX': 384, 'footY': 1480} if anchor == 'feet' else {}
        instruction = (f'Canvas {width}x{height} px. ' +
            ('All head layers exactly registered: center x=512, eye line y=560, shoulder line y=1050; head oval x=240..784, y=210..890. ' if anchor == 'eyes' else
             'Body center x=384, soles on y=1480, head fully inside top margin. ' if anchor == 'feet' else
             'Use the supplied 12x9 layout and guide: do not move furniture, floor or walkable cells. '))
        prompt = STYLE + instruction + ('Neutral grayscale for runtime tint, preserve highlights and shadows. ' if tint else 'Natural muted material colors. ') + detail
        result.append({'path': path, 'width': width, 'height': height, 'alpha': alpha, 'neutral': tint,
                       'registration': registration, 'allowEmpty': (allow_empty or '/hair_back/7.webp' in path or '/hair_front/7_' in path), 'maxBytes': 3 * 1024 * 1024 if anchor == 'scene' else 256 * 1024, 'prompt': prompt})
    groups = {
        'base': [(f'{sex}_{i}', True, f'{sex} adult face shape {name}; skin, ears and neck only, no features, hair or garment.')
                 for sex in ('female', 'male') for i, name in enumerate(('oval','round','angular','heart','narrow'))],
        'eyes': [(str(i), False, f'Eye shape {name}; photographic eyelids with natural skin chroma for decode-time separation, neutral sclera and lashes; iris color is supplied by a separate registered texture.') for i,name in enumerate(('almond','round','narrow','slanted'))] + [('iris_mask', True, 'Iris pair, pupil, reflections only; centered at x=417 and x=607, y=560.')],
        'brows': [(str(i), True, f'Eyebrow pair: {name}.') for i,name in enumerate(('straight','arched','bushy','fine'))],
        'nose': [(str(i), True, f'Nose: {name}, neutral skin shading at the shared registration; runtime skin tint.') for i,name in enumerate(('straight','aquiline','upturned','broad'))],
        'mouth': [(str(i), True, f'Mouth expression: {name}; runtime natural lip tint.') for i,name in enumerate(('calm','smiling','serious','determined'))],
        'hair_back': [(str(i), True, f'Back hair only: {name}; no face or front locks.') for i,name in enumerate(('short','parted','long loose','ponytail','bun','braided','wild','shaved'))],
        'hair_front': [(f'{i}_{j}', True, f'Front hair only: style {name}, texture {texture}.') for i,name in enumerate(('short','parted','long loose','ponytail','bun','braided','wild','shaved')) for j,texture in enumerate(('straight','wavy','curly'))],
        'beard': [(str(i), True, f'Beard only: {name}.') for i,name in enumerate(('stubble','goatee','full beard','long beard'),1)],
        'marks': [(f'scar_{i}', False, f'Healed adult scar: {name}.') for i,name in enumerate(('cheek','over eye','forehead','chin'),1)] + [(f'tattoo_{i}', False, f'Tattoo: {name}.') for i,name in enumerate(('tribal lines','elven vine','wall sigil'),1)],
        'outfit': [(str(i), True, f'Garment only: {name}.') for i,name in enumerate(('linen tunic','leather doublet','travel dress','gambeson','noble robe'))],
        'armor': [(str(i), False, f'Armor only, material {name}; no garment, head or body.') for i,name in enumerate(('leather','chainmail','scale','plate'),1)],
        'cloak': [(str(i), True, f'Cloak only: {name}; transparent center where face and garment remain visible.') for i,name in enumerate(('wool','fur','elven hood'),1)]
    }
    for group, entries in groups.items():
        for name, tint, detail in entries: add(f'portraits/{group}/{name}.webp',1024,1280,tint=tint,detail=detail)
    assert len(result) == 82
    for sex in ('female','male'):
        for pose in POSES:
            prefix=f'figures/{sex}/{pose}'
            details=f'Adult {sex}, {pose.replace("_", " ")} pose; front camera, same scale across all frames. '
            if pose.startswith('walk_'): details += f'Walk cycle phase {int(pose[-1])*25} percent. Opposite arm and leg move together; no camera movement. '
            for i in range(4): add(f'{prefix}/body/{i}.webp',768,1536,tint=True,detail=details+f'Body build {i}: slim, athletic, strong, stocky respectively; plain neutral underlayer; no hair or equipment.',anchor='feet')
            for group in ('outfit','armor','cloak','hair_front'):
                for name,tint,detail in groups[group]: add(f'{prefix}/{group}/{name}.webp',768,1536,tint=tint,detail=details+detail,anchor='feet')
            for i,weapon in enumerate(('longsword','bow','spear','battleaxe','twin daggers','staff')):
                add(f'{prefix}/weapon/{i}.webp',768,1536,detail=details+f'{weapon} only, aligned to hands/belt.',anchor='feet')
            add(f'{prefix}/shadow.webp',768,1536,detail=details+'Soft contact shadow only, ground contact at the foot line, upper-left key light.',anchor='feet')
    for scene in SCENES:
        add(f'scenes/{scene}/day.webp',2160,1440,alpha=False,detail=f'{scene} architecture and empty floor, no people or foreground furniture. Warm candlelit interior for tavern, natural daylight outdoors. Furniture is supplied as separate row occlusion layers.',anchor='scene')
        for row in range(9): add(f'scenes/{scene}/foreground_{row}.webp',2160,1440,detail=f'{scene} furniture belonging to engine row {row} only, use exact blocked cells from layout.json. Alpha elsewhere; full canvas registered to day.webp.' + (' This row contains no foreground objects: export a fully transparent technical layer.' if row not in SCENE_FOREGROUND_ROWS[scene] else ''),anchor='scene',allow_empty=row not in SCENE_FOREGROUND_ROWS[scene])
    return result
