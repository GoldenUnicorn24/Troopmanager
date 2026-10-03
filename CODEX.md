# Realm of the Last Wall 0.82.0

Stand vom 3. Oktober 2026. Android-Paket `com.goldenunicorn.troopmanager`, versionCode 38, versionName 0.82.0.

## Schwerpunkt dieser Version

v0.82 stabilisiert den großen 0.81-Ausbau und verbindet Frontier, Heer, Welt und UI enger miteinander.

### Behobene Kernfehler

- Feindliche Lager und neu gespawnte Grenzgruppen werden persistent gespeichert.
- Die zwei unabhängigen Raid-Systeme verwenden eindeutige IDs und beschädigen Langzeit-Saves nicht mehr durch doppelte Hordenkennungen.
- Ein gescheiterter Lagersturm verursacht echte Verluste im Armee- und Bevölkerungszustand.
- Lager und Beute werden erst nach einem tatsächlichen Sieg entfernt bzw. gutgeschrieben.
- Lagerstürme verwenden die reale Gegnerstärke statt einer künstlichen 800er-Obergrenze.
- Jagdverluste treffen echte ArmyPools und benannte Regimenter konsistent.
- Tagesziele besitzen eine konkrete Bedingung statt eines generischen 40-Soldaten-Checks.
- Automatischer Zuzug folgt vorhandenen/geförderten Kulturen statt pauschal Menschen zu erzeugen.
- Exakte Feldheere dürfen ihre tatsächlich anwesenden Soldaten einsetzen; Heimatgarnisonen können nicht in Feldschlachten teleportiert werden.
- Mauerwaffen verursachen ihren Schaden nur in echten Kampfrunden. Der frühere doppelte Vorab-Schaden beim Schlachtstart ist entfernt.

### Frontier und Strategie

- Eigener Frontier-Hauptbereich statt verstecktem sechsten Heer-Reiter.
- Grenzkarte mit passenden Ork-, Uruk- und Tao-Tei-Artworks, eigener/feindlicher Stärke, Risikohinweis, Kosten und Folgen.
- Außenposten: Wachturm, Versorgungsposten und Grenzfort, jeweils bis Stufe 3.
- Wachtürme erhöhen Vorwarnzeit, Versorgungsposten senken Patrouillenbedarf, Grenzforts bremsen Lagerwachstum.
- Entscheidbare Grenzereignisse statt reiner Gratisboni: Karawane, Gesandte, Flüchtlinge, Deserteure, Handwerker, Späher, Verwundete und Schmuggler.
- Völkerförderung und kulturabhängiger Zuzug bleiben Teil der langfristigen Bevölkerungsentwicklung.

### Eigene Regimenter und Hauptleute

- Eigene Regimenter führen Dienstakte mit Schlachten, Siegen und Verlusten.
- Veteranenstufen: Neu aufgestellt, Erprobt, Veteranen, Elite, Garde und Legenden.
- Hauptleute können einem eigenen Regiment fest zugewiesen werden.
- Hauptmannsangebote sind unterschiedliche Kandidaten mit Kultur, Führung, Taktik, Charakterzug und Preis.
- Veteranenstatus und Hauptmann wirken begrenzt auf die effektive Kampfkraft.

### UI / UX

- Fünf feste Hauptbereiche: Hof, Stadt, Heer, Grenze, Welt.
- Beziehung, Familie und weitere Verwaltungsseiten bleiben kontextuell erreichbar statt die Hauptleiste zu überladen.
- Die Grenzseite besitzt Lage, Verbündete, Mauer, Designs und Posten.
- Kommandozentrale zeigt priorisierte Warnungen für nahe Horden, Verwundete und negative Nahrungsbilanz.
- Doppelte vertikale Scroll-Fläche im Hof entfernt.
- Versionsanzeige im Hauptmenü kommt aus BuildConfig und kann nicht mehr manuell veralten.
- Das Menü-Symbol ist von der eigentlichen Hilfe getrennt.
- Freischalttext für eigene Regimenter entspricht wieder der Engine: Tag 40, drei Siege oder Kaserne 4.

## Build und Prüfung

Der Branch `codex/v0.82` ist in GitHub Actions eingebunden. Die Pipeline führt aus:

1. Unit-Tests
2. Android Lint
3. Debug-APK-Build
4. APK-Signaturprüfung
5. Paket-, versionCode- und versionName-Prüfung
6. Upload des Artefakts `Realm-of-the-Last-Wall-v0.82.0-debug`

Weiterentwicklung für diese Version erfolgt auf `codex/v0.82`, nicht auf `main`.
