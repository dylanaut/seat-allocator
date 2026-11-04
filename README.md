# Seat Allocator

Sitzplatzreservierung für Eisenbahnwagen mit Quarkus: Teilstrecken, Gruppen, Kontingente, Rollstuhl- und
Fahrradplätze, Ruhe- und Kinderbereich, temporäre Vormerkung und Szenarien mit Zeitraffer.

Die fachliche Dokumentation steht in [`src/main/asciidoc/project.adoc`](src/main/asciidoc/project.adoc).

## Voraussetzungen

- Java 21 und Maven 3.9
- Docker (Quarkus Dev Services startet PostgreSQL für `quarkus:dev` und die Tests)

## Entwickeln

```shell
mvn quarkus:dev          # http://localhost:8080/ (Reservieren), /szenarien.html (Szenarien)
mvn verify               # Tests, Anwendung und Dokumentation
```

`mvn verify` erzeugt aus `project.adoc` die Dokumentation in `target/generated-docs/`:
`seat-allocator-<version>.pdf` und `seat-allocator-<version>.html`. Mit `-Ddocs.skip=true` wird sie übersprungen.
Im PDF werden Formeln (`latexmath`) mit JLaTeXMath als Bilder gesetzt (Erweiterung `src/docs/asciidoctor/formeln-als-bilder.rb`), im HTML mit MathJax. Die Sequenzdiagramme (PlantUML) entstehen im Build über `asciidoctorj-diagram`; das HTML enthält die Bilder eingebettet (`data-uri`).

## Continuous Integration

`.github/workflows/ci.yml` baut und testet jeden Pull Request und jeden Push auf `main` und hängt die
Dokumentation als Artefakt `dokumentation` an den Lauf.

## Release erstellen

1. Den Pull Request nach `main` mergen, die CI muss grün sein.
2. Tag setzen und pushen (alternativ in GitHub unter *Releases > Draft a new release* mit neuem Tag `v1.0.0`):

   ```shell
   git checkout main && git pull
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. `.github/workflows/release.yml` setzt die Version aus dem Tag (das POM bleibt unverändert), führt Build und Tests aus
   und legt das GitHub Release `1.0.0` mit diesen Dateien an:
   - `seat-allocator-1.0.0.zip` (Anwendung als Quarkus-Verteilung samt Beispiel-Szenarien)
   - `seat-allocator-1.0.0.pdf` und `seat-allocator-1.0.0.html` (Dokumentation)

Tags mit Suffix (z. B. `v1.1.0-rc.1`) werden als Vorabversion veröffentlicht. Über *Actions > Release > Run workflow*
lässt sich ein Probelauf ohne Release starten; die Dateien liegen dann als Workflow-Artefakt vor.

## Anwendung aus dem Release starten

```shell
unzip seat-allocator-1.0.0.zip && cd seat-allocator-1.0.0
java -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/seats \
     -Dquarkus.datasource.username=... -Dquarkus.datasource.password=... \
     -jar quarkus-run.jar
```

Ohne Dev Services braucht die Anwendung eine PostgreSQL-Datenbank. Beim Start wird das Schema neu erzeugt und mit
Demodaten (`import.sql`) gefüllt (`quarkus.hibernate-orm.database.generation=drop-and-create`). Szenarien liegen als
JSON-Dateien im Verzeichnis `szenarien` relativ zum Arbeitsverzeichnis (`zuteilung.szenarien.verzeichnis`).
