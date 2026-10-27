import importlib.machinery
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from PIL import Image, ImageDraw
from asset_contract import catalog
loader=importlib.machinery.SourceFileLoader('asset_checker',str(Path(__file__).with_name('check_assets')))
spec=importlib.util.spec_from_loader(loader.name,loader); checker=importlib.util.module_from_spec(spec); loader.exec_module(checker)

class AssetCheckerTest(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory(); self.addCleanup(self.directory.cleanup)
        self.root=Path(self.directory.name); self.name='portraits/base/female_0.webp'; self.path=self.root/self.name
        self.path.parent.mkdir(parents=True)
        im=Image.new('RGBA',(1024,1280)); ImageDraw.Draw(im).ellipse((240,210,784,890),fill=(160,160,160,255)); im.save(self.path,lossless=True)
        self.meta={'registration':{'centerX':512,'eyeY':560},'source':'Geometrische Testzeichnung','license':'CC0-1.0'}
        self.write_meta()
    def write_meta(self): self.path.with_suffix('.webp.json').write_text(json.dumps(self.meta))
    def report(self): return checker.check(self.root,self.name)
    def test_catalog_has_exactly_82_portrait_layers(self):
        self.assertEqual(82,len([a for a in catalog() if a['path'].startswith('portraits/')]))
        self.assertEqual(len(catalog()),len({a['path'] for a in catalog()}))
    def test_missing_is_reported_without_hiding_valid_existing_art(self):
        result=self.report(); self.assertEqual([],result['errors']); self.assertEqual(1,result['installed']); self.assertEqual(len(catalog())-1,len(result['missing']))
    def test_wrong_size_is_an_error(self):
        Image.new('RGBA',(512,640)).save(self.path); self.assertIn('Maße',self.report()['errors'][0]['error'])
    def test_opaque_layer_is_an_error(self):
        Image.new('RGB',(1024,1280),'gray').save(self.path); self.assertIn('Alpha',self.report()['errors'][0]['error'])
    def test_wrong_eye_registration_is_an_error(self):
        self.meta['registration']['eyeY']=559; self.write_meta(); self.assertIn('Augen',self.report()['errors'][0]['error'])
    def test_missing_source_is_an_error(self):
        del self.meta['source']; self.write_meta(); self.assertIn('Herkunft',self.report()['errors'][0]['error'])
    def test_tinted_layers_must_be_gray(self):
        im=Image.new('RGBA',(1024,1280)); ImageDraw.Draw(im).ellipse((240,210,784,890),fill='red'); im.save(self.path,lossless=True)
        self.assertIn('grau',self.report()['errors'][0]['error'])
    def test_unknown_filename_is_an_error(self):
        (self.path.parent/'typo.webp').write_bytes(self.path.read_bytes()); self.assertTrue(any('unbekannt' in e['error'] for e in self.report()['errors']))
    def test_size_budget_is_checked_before_decode(self):
        self.path.write_bytes(self.path.read_bytes()+b'\0'*262144); self.assertIn('budget',self.report()['errors'][0]['error'])

    def test_empty_skin_is_rejected(self):
        Image.new('RGBA',(1024,1280)).save(self.path,lossless=True)
        self.assertIn('Alpha',self.report()['errors'][0]['error'])
    def scene_row(self,row,declared_empty):
        self.name=f'scenes/tavern/foreground_{row}.webp';self.path=self.root/self.name
        self.path.parent.mkdir(parents=True,exist_ok=True)
        Image.new('RGBA',(2160,1440)).save(self.path,lossless=True)
        self.meta['registration']={};self.write_meta()
        (self.path.parent/'layout.json').write_text(json.dumps({'foreground':[{'row':row,'empty':declared_empty}]}))
    def test_empty_furniture_row_is_valid(self):
        self.scene_row(0,True)
        self.assertFalse(any(e['path']==self.name for e in self.report()['errors']))
    def test_occupied_row_cannot_be_declared_empty(self):
        self.scene_row(2,True)
        self.assertTrue(any(e['path']==self.name for e in self.report()['errors']))
    def test_empty_row_layout_must_match_image(self):
        self.scene_row(0,False)
        self.assertTrue(any('Layout' in e['error'] for e in self.report()['errors']))

if __name__=='__main__':unittest.main()
