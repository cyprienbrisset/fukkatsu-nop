<div align="center">

# 復活 Fukkatsu

**Launcher maison pour Meta Portal Plus (1ère & 2ème génération)**

*Encre japonaise · Ambiant permanent · Sans Google Services*

---

[![Android 10](https://img.shields.io/badge/Android-10-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/cyprienbrisset/fukkatsu)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/cyprienbrisset/fukkatsu)
[![Licence MIT](https://img.shields.io/badge/Licence-MIT-C1272D?style=flat-square)](LICENSE)

</div>

---

**復活** (*fukkatsu*, « renaissance ») transforme un Meta Portal en hub maison permanent. Testé sur le **Portal Plus 1ère génération** et le **Portal 2ème génération**. L'interface s'inspire de la papeterie japonaise : encre Sumi profonde la nuit, parchemin Washi chaud le jour. Tout fonctionne sans connexion Google — pas de compte, pas de Play Store requis.

---

## Ce que vous voyez au quotidien

**Une horloge ambiante** occupe la gauche de l'écran en permanence — heure, date, météo en direct et prochain réveil d'un seul coup d'œil. Quand de la musique joue, la pochette et les commandes apparaissent dessous. Les six derniers contacts Messenger ou WhatsApp avec qui vous avez échangé sont également affichés.

**La grille d'apps** à droite regroupe vos raccourcis en médaillons. Un tap lance l'app. Un badge rouge indique le nombre de notifications non lues. Appui long sur une tuile = actions rapides ; appui long dans la zone vide = mode réorganisation par glisser-déposer.

Le thème bascule automatiquement entre mode nuit (Sumi) et mode jour (Washi) selon l'heure — 20h pour la nuit, 7h pour le jour. L'accent rouge vermillon **朱** reste constant.

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

## FukkaStore — installer des apps sans Google

Le Portal n'a pas de Play Store. FukkaStore comble ce manque : connectez votre compte Google une seule fois, puis parcourez et installez des applications directement depuis le catalogue officiel Google Play.

- Navigation par catégories (Productivité, Musique, Réseaux sociaux…)
- Recherche en texte libre
- Tap sur une app pour afficher sa fiche complète (description, captures d'écran, note)
- Une seule pression pour télécharger et installer
- Filtre automatique des apps incompatibles avec le Portal

---

## Intégration Google

Un onglet dédié regroupe les accès Google :

- **Agenda** — vos prochains événements, avec un bouton *Rejoindre* direct pour les réunions Google Meet
- **Raccourcis** — Chat, Meet et Calendar s'ouvrent en un tap si l'app est installée

---

## Réveil intégré

Un gestionnaire de réveils complet, intégré au launcher : création par heure et jours de la semaine, sonnerie personnalisable, montée en volume progressive. Le prochain réveil est affiché en permanence sur l'écran ambiant.

---

## Thèmes

<table>
<tr>
<th align="center">🌙 Sumi — Nuit (20h – 7h)</th>
<th align="center">☀️ Washi — Jour (7h – 20h)</th>
</tr>
<tr>
<td>Fond quasi-noir <code>#0D0E12</code>, texte ivoire <code>#ECE7DD</code></td>
<td>Fond parchemin <code>#F2EDE3</code>, texte encre <code>#14161C</code></td>
</tr>
</table>

Typographie **Noto Serif JP** (Mincho). Accent vermillon **朱** `#C1272D` invariant.

---

## Installation rapide

Le Portal doit avoir le **débogage USB** activé. Branchez-le et lancez :

```bash
bash scripts/provision-portal.sh --disable-verifier --set-launcher
```

Ce script construit l'APK, l'installe, accorde les permissions nécessaires (Ne Pas Déranger, luminosité, administrateur d'appareil) et définit Fukkatsu comme launcher par défaut.

---

## Licence

MIT — Copyright © 2024–2026 Cyprien Brisset
