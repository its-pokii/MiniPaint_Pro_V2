# MiniPaint Pro V2 — Application complète Java Swing

**Version 2.0.0 · Java 21+ · Swing + Graphics2D · Windows, Linux, macOS**

MiniPaint Pro V2 est un éditeur d'image de bureau. Aucun framework tiers n'est nécessaire pour lancer le JAR. Le projet conserve les fonctionnalités de MiniPaint Pro V1 et ajoute l'édition d'images, les filtres, l'édition de texte avancée, l'opacité/fusion de calques et plusieurs dessins ouverts dans des onglets.

## Démarrage sur Windows (aucun Maven nécessaire)

1. Extraire `MiniPaint_Pro_V2_Complet.zip`.
2. Ouvrir le dossier `MiniPaint_Pro_V2`.
3. Double-cliquer sur **`demarrer.bat`**.
4. Si le script ne se lance pas, ouvrir PowerShell dans ce dossier puis exécuter :

   ```powershell
   java -version
   java -jar .\MiniPaintProV2.jar
   ```

**Compatible avec Java 21** : ne nécessite pas Java 25. Si une autre version de Java est prioritaire dans le PATH, utiliser explicitement le chemin de l'exécutable Java 21.

## Ouvrir le code source dans IntelliJ IDEA

1. `File > Open`, puis choisir le dossier `MiniPaint_Pro_V2`.
2. Vérifier le SDK de projet : `File > Project Structure > SDK > JDK 21`.
3. Accepter l'import Maven si IntelliJ le demande (ou ouvrir comme projet Java).
4. Ouvrir `src/main/java/com/minipaint/Main.java` et cliquer sur `Run 'Main.main()'`.
5. Modifier les fichiers Java puis reconstruire avec `compiler.bat` ou Maven.

## Fonctionnalités existantes (V1) conservées

- Crayon et gomme (avec clic simple), aérosol et pot de peinture.
- Pipette de couleur, sélecteur de couleurs, palette, épaisseur et opacité du pinceau.
- Ligne, rectangle, rectangle rempli, ellipse, ellipse remplie, triangle, flèche.
- Sélection rectangulaire, déplacement, copier/coller, couper, suppression.
- Annuler/Rétablir (historique propre à chaque onglet).
- Zoom, couleurs et mode clair/sombre.
- Calques : ajout, suppression, nom, visibilité, monter/descendre.
- Import PNG/JPEG/BMP, export PNG/JPEG/BMP, projet natif `.mpaint` avec calques.
- Sauvegarde automatique (désormais `.mpaint` pour préserver les calques) toutes les 2 minutes lorsque des dessins sont modifiés.

## Fonctionnalités nouvelles (V2)

### Image

Dans le menu **Image** :
- **Recadrer selon la sélection** : choisir l'outil « Sélection », encadrer une région puis recadrer. Tous les calques sont recadrés ensemble.
- **Rotation** : 90° horaire, 90° antihoraire, 180°.
- **Miroir** : horizontal et vertical.
- **Redimensionner l'image** : mise à l'échelle des pixels de tous les calques avec interpolation bicubique. Ne pas confondre avec « Redimensionner la zone de dessin », qui change la surface sans étirer l'image.

### Filtres

Dans le menu **Filtres**, appliquer sur le **calque actif** :
- **Flou** et **Netteté** (1 à 5 passes de filtre 3×3).
- **Luminosité / Contraste** (valeurs entre -100 et +100).

Les filtres préservent la couche alpha du calque et restent annulables avec `Ctrl+Z`. Les traitements volumineux peuvent prendre du temps selon les dimensions et la machine.

### Texte avancé

Sélectionner l'outil **Texte**, puis cliquer sur la surface de dessin. La boîte de dialogue propose :
- Texte multiligne ; famille de polices disponible sur l'ordinateur ; taille entre 8 et 240 ; gras ; italique ; ombre ; alignement gauche/centre/droite.
- Le texte est rasterisé dans le calque actif après validation (pas de réédition vectorielle après insertion).

### Calques améliorés

Dans le panneau **Calques** ou le menu :
- **Opacité du calque** de 0 à 100 % (indépendante de l'opacité du pinceau).
- **Dupliquer le calque**, **Fusionner vers le bas** (uniquement si les deux calques concernés sont visibles).
- L'opacité de chaque calque est conservée dans `.mpaint`. Les anciens fichiers V1 sont compatibles et prennent 100 % par défaut.

### Dessins multiples

- **+ Onglet**, `Fichier > Nouveau`, ou `Ctrl+N` : ouvre un nouveau dessin sans remplacer le précédent.
- **Ouvrir une image / projet** : ouvre un nouvel onglet.
- Chaque onglet possède ses dimensions, calques, document, historique et fichiers de sauvegarde.
- **Fermer onglet** ou `Ctrl+W` demande confirmation si les changements de l'onglet sont non enregistrés.
- Un astérisque `*` indique les modifications non sauvegardées.

## Sauvegarde

- **Enregistrer le projet `.mpaint`** (`Ctrl+P`) : conserve le document et les calques, les noms, la visibilité, l'opacité et les dimensions. **Recommandé** pour un travail à reprendre.
- **Enregistrer l'image** (`Ctrl+S`) : aplati le rendu vers PNG, JPG ou BMP ; les calques ne sont pas enregistrés dans l'image.
- **Exporter sous** (`Ctrl+Maj+S`) : choisir le format PNG/JPEG/BMP.
- Le fond exporté est blanc. Pour retrouver l'opacité/calques, sauver aussi un projet `.mpaint`.
- Sauvegarde auto : `%USERPROFILE%\MiniPaint\autosave-onglet-N.mpaint` (Windows). Le nom `N` dépend de l'ordre des onglets ; il vaut mieux aussi sauvegarder manuellement les projets importants.

## Raccourcis clavier

| Raccourci | Commande |
|---|---|
| `Ctrl+N` | Nouveau dessin dans un onglet |
| `Ctrl+W` | Fermer l'onglet actif |
| `Ctrl+O` | Ouvrir image |
| `Ctrl+P` | Enregistrer projet `.mpaint` |
| `Ctrl+S` | Enregistrer image |
| `Ctrl+Maj+S` | Exporter sous |
| `Ctrl+Z` / `Ctrl+Y` | Annuler / Rétablir |
| `Ctrl+A` | Tout sélectionner |
| `Ctrl+C` / `Ctrl+X` / `Ctrl+V` | Copier / Couper / Coller |
| `Ctrl+D` / `Échap` | Désélectionner |
| `Suppr` | Effacer sélection |
| `Ctrl+Maj+N` | Nouveau calque |
| `Ctrl + molette` | Zoomer/dézoomer |

## Arborescence

```text
MiniPaint_Pro_V2/
├── src/main/java/com/minipaint/
│   ├── Main.java                  # Entrée de l'application
│   ├── edit/ImageEditor.java      # Crop, rotate, flip, scale et filtres
│   ├── io/ProjectIO.java          # Lecture/écriture du format .mpaint
│   ├── model/PaintDocument.java   # Document, rendu et fusion des calques
│   ├── model/PaintLayer.java      # Calque ARGB, visibilité, opacité
│   ├── model/Tool.java            # Outils de dessin
│   └── ui/
│       ├── PaintCanvas.java       # Dessin, zoom, historique, texte, filtres
│       └── PaintFrame.java        # Fenêtre, menus, onglets, dialogues
├── src/test/java/com/minipaint/
│   ├── MiniPaintSmokeTest.java
│   ├── ProjectRoundtripTest.java
│   ├── Version2FeatureTest.java
│   ├── TabIntegrationTest.java
│   └── UiSmokeTest.java
├── screenshots/apercu-minipaint-pro-v2.png
├── MiniPaintProV2.jar
├── pom.xml
├── compiler.bat
├── demarrer.bat
└── README.md
```

## Recompiler le JAR

**Sans Maven, sous Windows** : `compiler.bat` (JDK 21 requis, `javac` et `jar` disponibles dans le PATH).

**Avec Maven** :

```powershell
mvn clean package
java -jar target\minipaint-pro-2.0.0.jar
```

**Avec javac (tout OS, en étant dans le dossier du projet)** :

```bash
mkdir -p build/classes
javac --release 21 -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')
jar --create --file MiniPaintProV2.jar --main-class com.minipaint.Main -C build/classes .
java -jar MiniPaintProV2.jar
```

Les deux commandes ci-dessus utilisent la syntaxe Bash/Linux ; pour Windows sans Bash, utiliser `compiler.bat`.

## Tests exécutés (Java 21)

- Suite V1 : pinceau, gomme, formes, Undo/Redo, remplissage, presse-papiers, calques, PNG.
- Test de chargement et sauvegarde `.mpaint` avec métadonnées Unicode.
- Suite V2 : recadrage, rotation, miroirs, redimensionnement, filtres, transparence, sauvegarde de l'opacité, duplication, fusion, Undo/Redo.
- Tests GUI sous affichage virtuel : onglets indépendants, indicateurs de modification et démarrage réel de la fenêtre Swing.

**Limitations connues** : le texte ajouté devient une image raster ; le PNG/JPG/BMP fusionne les calques ; les filtres sont calculés sur le calque actif ; les très grandes images et grands nombres de calques sont limités afin de réduire la consommation mémoire ; l'historique conserve jusqu'à 18 états par onglet.
