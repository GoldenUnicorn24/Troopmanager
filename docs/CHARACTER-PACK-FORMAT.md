# Offline-Charakterpaket, Version 1

Ein Paket ist eine ZIP-Datei mit genau `manifest.json`, `portrait.webp` und optional
`fullbody.webp`. Keine Verzeichnisse, doppelten Einträge, absoluten Pfade, `..` oder weiteren
Dateien. ZIP-Dateien werden nicht in frei gewählte Pfade entpackt. Die App liest begrenzte
Einträge in den Speicher, prüft sie und erzeugt eine neue interne Bild-ID.

Das Manifest ist UTF-8 und höchstens 64 KiB groß. Jedes Bild ist höchstens 15 MiB groß.
Das gesamte ZIP darf höchstens 32 MiB groß sein. Porträt: exakt 1024 × 1280, WebP.
Ganzkörper: exakt 768 × 1536, WebP mit Transparenz. Registrierung wie in ASSET-STANDARD.md.
Die App übernimmt keine Beziehungen, Erinnerungen, Geld, Ausrüstung oder Spielstand-IDs aus Paketen.
Importierte Personen beginnen als normale Fremde mit einem eigenen Tagesablauf.

```json
{
  "format": "troopmanager-character",
  "version": 1,
  "character": {
    "name": "Mira Testfigur",
    "age": 27,
    "gender": "FEMALE",
    "species": "HUMAN",
    "culture": "HUMAN",
    "origin": "Rabenfurt",
    "profession": "Kräuterkundige",
    "personality": {"warmth": 65, "boldness": 45, "honor": 70, "humor": 50, "curiosity": 75, "temper": 25},
    "interests": ["Kräuterkunde", "alte Lieder"],
    "role": "REGULAR",
    "homeProvinceId": "gm_rabenfurt",
    "romanceable": false,
    "appearance": {}
  },
  "source": "Selbst erstellte geometrische Testfigur, CC0-1.0",
  "portrait": "portrait.webp",
  "fullbody": "fullbody.webp"
}
```

`age` ist Pflicht und muss mindestens 18 sein. Herkunft und Rechte müssen unter `source`
angegeben werden. Die Bestätigung der Rechte und Volljährigkeit ist auch beim Paketimport
notwendig. Eine Deklaration im Manifest ersetzt keine tatsächlichen Bildrechte.
`fullbody` darf fehlen oder null sein. Optionale Erscheinungswerte entsprechen
`CharacterAppearance`; interne Bild-IDs werden nie aus Paketen übernommen.

JSON-Schema: [character-pack.schema.json](character-pack/character-pack.schema.json).
[Beispielpaket](character-pack/example-character.zip), [Beispielmanifest](character-pack/example-manifest.json).
Das Beispielpaket enthält ausschließlich selbst erstellte geometrische Testgrafiken;
es dient dem Format- und Importtest, nicht als realistisches Spiel-Artwork.

Paketexport öffnet das Android-Teilen-Menü mit einer zeitlich freigegebenen Content-URI aus
dem App-Cache. Es wird kein Netzwerkdienst benötigt. Die Originale werden intern kopiert.

Implementierungsstatus und Prüfnachweise: [Testbericht](CUSTOM-CHARACTER-TEST-REPORT.md).
