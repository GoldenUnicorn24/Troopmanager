#!/usr/bin/env python3
"""Check the documented, self-created sample against schema and actual encoded images."""
import io,json,zipfile
from pathlib import Path
from PIL import Image
import jsonschema
root=Path(__file__).resolve().parents[1]/'docs/character-pack'
with zipfile.ZipFile(root/'example-character.zip') as z:
    assert set(z.namelist())=={'manifest.json','portrait.webp','fullbody.webp'}
    manifest=json.loads(z.read('manifest.json'))
    jsonschema.validate(manifest,json.loads((root/'character-pack.schema.json').read_text()))
    assert manifest==json.loads((root/'example-manifest.json').read_text())
    for name,size in [('portrait.webp',(1024,1280)),('fullbody.webp',(768,1536))]:
        data=z.read(name); assert len(data)<=15*1024*1024
        with Image.open(io.BytesIO(data)) as im:
            im.load(); assert im.format=='WEBP' and im.size==size
            if name=='fullbody.webp': assert im.mode=='RGBA' and im.getextrema()[3][0]==0 and im.getextrema()[3][1]>0
print('Example ZIP: schema, adult age, filenames, real WebP dimensions and transparency verified.')
