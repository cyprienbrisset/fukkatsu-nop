<div align="center">

# 復活 Fukkatsu No P

**Launcher maison pour Meta Portal — Portal Plus, Portal Go et Portal Mini**

*Encre japonaise · Ambiant permanent · Sans Google Services*

---

[![Android 9/10](https://img.shields.io/badge/Android-9%20%2F%2010-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/cyprienbrisset/fukkatsu-nop)
[![Portal Plus · Go · Mini](https://img.shields.io/badge/Meta%20Portal-Plus%20%7C%20Go%20%7C%20Mini-4267B2?style=flat-square)](https://github.com/cyprienbrisset/fukkatsu-nop)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/cyprienbrisset/fukkatsu-nop)
[![Licence MIT](https://img.shields.io/badge/Licence-MIT-C1272D?style=flat-square)](LICENSE)

</div>

---

**復活** (*fukkatsu no p*, « renaissance ») transforme un Meta Portal en hub maison permanent. Testé sur le **Portal Plus 1ère génération** (Android 9, API 28), le **Portal 2ème génération** (Android 10, API 29), le **Portal Go** et le **Portal Mini** (1280×800 dp, mode compact automatique). L'interface s'inspire de la papeterie japonaise : encre Sumi profonde la nuit, parchemin Washi chaud le jour.

---

## Ce que vous voyez au quotidien

**Une horloge ambiante** occupe la gauche de l'écran en permanence — heure, date, météo en direct et prochain réveil d'un seul coup d'œil. Si plusieurs villes sont configurées, le nom de la ville et un indicateur de points s'affichent sous la température ; un swipe gauche/droite change de ville. Quand de la musique joue, la pochette et les commandes apparaissent dessous. Les six derniers contacts Messenger ou WhatsApp avec qui vous avez échangé sont également affichés.

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
| **Glisser gauche/droite** sur la météo | Passer à la ville suivante / précédente (si multi-villes configurées) |
| **Appui long** sur une tuile | Actions rapides : raccourcis, déplacer, supprimer |
| **Appui long** dans la zone vide du grid | Entrer en mode réorganisation — glisser pour déplacer, « Terminé » pour quitter |

---

## Intégration Google — Agenda & Meet

Un troisième écran (glisser vers la droite depuis l'accueil) connecte le Portal à votre compte Google via le **Device Authorization Flow** — un code s'affiche sur le Portal, vous l'approuvez depuis n'importe quel autre appareil, zéro navigateur requis sur le Portal :

- **Agenda** : événements des 60 prochains jours, groupés par jour, indicateur vermillon pour les réunions imminentes (< 1 h). Créez un événement directement depuis le Portal via le bouton « + ».
- **Meet** : réunions Google Meet des 48 prochaines heures avec compte à rebours en direct et bouton « Rejoindre ».

Scope limité à `calendar` (lecture/écriture). Aucune dépendance Gmail. Tokens stockés localement via DataStore.

---

## Mode compact — Portal Go &amp; Mini

Sur les appareils à résolution 1280×800 dp (Portal Go 10,1" et Portal Mini 8"), le launcher détecte automatiquement la taille d'écran et active un mode compact : horloge et kanjis réduits, espacement serré, grille avec tuiles plus petites. Aucune configuration manuelle requise.

---

## Surveillance firmware

Un `AlarmManager` vérifie quotidiennement `android.os.Build.DISPLAY`. Si la valeur change (mise à jour OTA Meta), une notification haute priorité alerte immédiatement — permettant de vérifier que l'ADB et les overlays système restent fonctionnels après la mise à jour. La version courante du firmware est visible dans **Réglages → Surveillance firmware**.

---

## Thèmes saisonniers

L'accent vermillon **朱** adopte une variante saisonnière automatique :

| Saison | Mois | Couleur |
|---|---|---|
| 桜 Sakura | Mars — Mai | Rose `#E8A0AF` |
| 朱 Shu | Juin — Août, Déc. | Vermillon `#C1272D` |
| 紅葉 Momiji | Septembre — Novembre | Orangé `#C85A14` |

La bascule est entièrement automatique, sans configuration. Dans l'esprit de la papeterie japonaise du projet.

---

## Commandes vocales locales — sans cloud

Le micro-réseau du Portal est réactivé via **Vosk**, moteur de reconnaissance 100% embarqué (zéro cloud, zéro GMS) :

- Dites **« Portal »** — le wake word est détecté localement en continu, aucun bouton requis
- **Commandes** : `ouvre [nom app]`, `mode nuit`, `mode jour`, `éteins l'écran`, `météo`, `alarme`
- Retour visuel : bannière en bas de l'écran pendant la fenêtre d'écoute (5 s)
- Modèle `vosk-model-small-fr-0.22` (~40 MB), téléchargé à la première activation depuis **Réglages → Commandes vocales**

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
- **Avertissement GMS** : les apps nécessitant exclusivement les Google Mobile Services sont signalées — elles peuvent ne pas fonctionner sur le Portal

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

Un gestionnaire de réveils complet : création par heure et jours de la semaine, sonnerie personnalisable, montée en volume progressive. Le prochain réveil est affiché en permanence sur l'écran ambiant. Trois sonneries disponibles : Standard, Zen et **Asie/Japon** (musique d'ambiance japonaise — *Tunetank / Pixabay Content License*).

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
