<div align="center">

# 復活 Fukkatsu No P

**Launcher maison pour Meta Portal Plus (1ère & 2ème génération)**

*Encre japonaise · Ambiant permanent · Sans Google Services*

---

[![Android 9/10](https://img.shields.io/badge/Android-9%20%2F%2010-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/cyprienbrisset/fukkatsu-nop)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/cyprienbrisset/fukkatsu-nop)
[![Licence MIT](https://img.shields.io/badge/Licence-MIT-C1272D?style=flat-square)](LICENSE)

</div>

---

**復活** (*fukkatsu no p*, « renaissance ») transforme un Meta Portal en hub maison permanent. Testé sur le **Portal Plus 1ère génération** (Android 9, API 28) et le **Portal 2ème génération** (Android 10, API 29). L'interface s'inspire de la papeterie japonaise : encre Sumi profonde la nuit, parchemin Washi chaud le jour.

---

## Ce que vous voyez au quotidien

**Une horloge ambiante** occupe la gauche de l'écran en permanence — heure, date, météo en direct et prochain réveil d'un seul coup d'œil. Quand de la musique joue, la pochette et les commandes apparaissent dessous. Les six derniers contacts Messenger ou WhatsApp avec qui vous avez échangé sont également affichés.

**La grille d'apps** à droite regroupe vos raccourcis en médaillons. Un tap lance l'app. Un badge rouge indique le nombre de notifications non lues. Appui long sur une tuile = actions rapides ; appui long dans la zone vide = mode réorganisation par glisser-déposer.

Le thème bascule automatiquement entre mode nuit (Sumi) et mode jour (Washi) selon l'heure configurable. Un bouton discret à côté des réglages permet de basculer manuellement à tout moment.

---

## Gestes

| Geste | Effet |
|---|---|
| Glisser **vers le haut** sur le logo | Recherche rapide parmi toutes les apps installées |
| Glisser **vers le bas** sur le logo | Activer / couper le mode Ne Pas Déranger |
| **Écarter deux doigts** (pinch-out) | Vue multitâche : cartes des apps récentes, glisser vers le haut pour fermer |
| **Glisser le bord droit** de l'écran | Régler la luminosité (curseur vertical) |
| **Appui long** sur une tuile | Actions rapides : raccourcis, déplacer, supprimer |
| **Appui long** dans la zone vide du grid | Entrer en mode réorganisation — glisser pour déplacer, « Terminé » pour quitter |

---

## FukkaStore — installer des apps sans Google Play

Le Portal n'a pas de Play Store. FukkaStore comble ce manque : connectez votre compte Google une seule fois, puis parcourez et installez des applications directement depuis le catalogue officiel Google Play.

- Navigation par catégories (Productivité, Musique, Réseaux sociaux…)
- Recherche en texte libre
- Tap sur une app pour afficher sa fiche complète (description, captures d'écran, note)
- Une seule pression pour télécharger et installer
- Filtre automatique des apps incompatibles avec le Portal
- Filtre ABI automatique : seuls les splits compatibles avec le processeur de l'appareil sont installés
- **Portal 1ère génération** : installation silencieuse via AccessibilityService (l'interface système du Portal masque la fenêtre de confirmation standard)

---

## Contrôles système — overlay universel

Un panneau flottant accessible depuis n'importe quelle app :

- **Volume** musique avec curseur + icône Bluetooth interactive (voir appareil connecté, liste des jumelés, ouvrir les réglages)
- **Luminosité** — curseur dédié
- **Mode nuit** — bascule jour/nuit instantanée
- **Retour accueil** en un tap

---

## Thème automatique

L'accent vermillon **朱** `#C1272D` reste invariant. Deux palettes :

<table>
<tr>
<th align="center">🌙 Sumi — Nuit</th>
<th align="center">☀️ Washi — Jour</th>
</tr>
<tr>
<td>Fond quasi-noir <code>#0D0E12</code>, texte ivoire <code>#ECE7DD</code></td>
<td>Fond parchemin <code>#F2EDE3</code>, texte encre <code>#14161C</code></td>
</tr>
</table>

Le basculement se fait à l'heure configurée dans **Réglages → Mode nuit automatique** (AlarmManager, survit aux redémarrages). Un bouton dans l'écran d'accueil et l'overlay permet de forcer le mode à tout moment.

Typographie **Noto Serif JP** (Mincho).

---

## Réveil intégré

Un gestionnaire de réveils complet : création par heure et jours de la semaine, sonnerie personnalisable, montée en volume progressive. Le prochain réveil est affiché en permanence sur l'écran ambiant.

---

## Fiabilité

Un **watchdog** de crash est intégré : si l'app plante, un `AlarmManager` déclenche un redémarrage automatique dans la seconde — sans dialogue « Appli arrêtée », sans délai système.

---

## Splash screen

Au démarrage, un **hanko seal 復** s'anime en rouge vermillon sur fond Sumi — fondu entrant, maintien, fondu sortant. Aucun écran blanc interstitiel.

---

## Installation rapide

Le Portal doit avoir le **débogage USB** activé. Branchez-le et lancez :

```bash
bash scripts/provision-portal.sh --set-launcher
```

Ce script construit l'APK si besoin, l'installe, et accorde automatiquement toutes les permissions requises via ADB :

| Permission | Rôle |
|---|---|
| `WRITE_SECURE_SETTINGS` | Mode nuit système + AccessibilityService d'auto-install |
| `WRITE_SETTINGS` (appops) | Contrôle de la luminosité |
| Notification Policy | Accès Ne Pas Déranger |
| Device Admin | Extinction de l'écran |
| `BLUETOOTH_CONNECT` | Noms et liste des appareils Bluetooth jumelés |
| Verifier désactivé | Empêche les blocages d'installation Play Protect (pas de GMS) |

Voir `docs/technical.md` pour le détail technique complet.

---

## Développement

```bash
# Build + install + relance (device connecté)
./gradlew deployDebug
```

Nécessite Android Studio (JDK intégré) — pas de `java` global requis.

---

## Licence

MIT — Copyright © 2024–2026 Cyprien Brisset
