# AirPlay Receiver — Design Spec
_2026-09-05_

## Objectif

Permettre au Portal de se comporter comme un second écran AirPlay pour un MacBook. Le Mac utilise "Partage d'écran" (Control Center → Screen Mirroring) pour projeter son bureau sur le Portal. Intégré directement dans le launcher Fukkatsu : tuile "Écran Mac" dans la grille, basculement automatique en plein écran dès que le Mac se connecte.

---

## 1. Protocole AirPlay Mirroring (legacy / AirPlay 1)

Le Portal implémente le sous-ensemble AirPlay Screen Mirroring de première génération, reverse-engineered et documenté publiquement (cf. RPiPlay, UxPlay). Ce protocole ne requiert **pas** les clés FairPlay d'AirPlay 2.

### Flux de session

```
Mac                                Portal (AirPlayHttpServer :7000)
 │  mDNS discover _airplay._tcp        │
 │ ─────────────────────────────────── │
 │  POST /fp-setup (FairPlay legacy)   │
 │ ─────────────────────────────────── │
 │  ← 200 OK + fp-setup-ack            │
 │                                     │
 │  OPTIONS rtsp://portal/stream       │
 │  ANNOUNCE (SDP — codec H.264)       │
 │  SETUP (RTP port négocié)           │
 │  RECORD                             │
 │ ─────────────────────────────────── │
 │  RTP UDP packets (H.264 AES-128)    │
 │ ─────────────────────────────────── │
 │  TEARDOWN                           │
```

### Chiffrement vidéo
Les paquets RTP sont chiffrés AES-128-CBC. La clé et l'IV sont transmis dans les en-têtes RTSP lors du SETUP, en clair (protocol legacy). Le `RtpVideoReceiver` les extrait et les passe au décodeur.

---

## 2. Architecture

```
AirPlayService (ForegroundService)
    └── AirPlayReceiver (orchestrateur)
            ├── MdnsAdvertiser          mDNS _airplay._tcp:7000
            ├── AirPlayHttpServer       HTTP :7000 (fp-setup, RTSP)
            ├── RtpVideoReceiver        UDP socket, AES-128 decrypt
            └── StateFlow<AirPlayState> partagé avec le reste de l'app
```

**`AirPlayState`** (sealed class) :
```kotlin
sealed class AirPlayState {
    object Waiting    : AirPlayState()   // mDNS actif, pas de Mac connecté
    object Connecting : AirPlayState()   // handshake RTSP en cours
    object Streaming  : AirPlayState()   // vidéo en cours
    data class Error(val msg: String)   : AirPlayState()
}
```

**`H264Renderer`** : reçoit les NAL units depuis `RtpVideoReceiver`, les pousse vers un `MediaCodec` configuré en mode Surface. La surface de sortie est fournie par `AirPlayActivity` via `AirPlayReceiver.attachSurface(surface)` quand l'Activity démarre, et `detachSurface()` quand elle s'arrête. Le `MediaCodec` est réconfiguré à chaque `attachSurface`.

---

## 3. Tuile "Écran Mac"

### TileType
`TileType.AIRPLAY` ajouté à l'enum existant. Stocké en DB comme les autres tiles.

### Rendu dans MedallionGrid
La tuile AIRPLAY a un rendu spécial (comme les tuiles WEB) :

| `AirPlayState` | Apparence |
|---|---|
| `Waiting` | Fond normal, label "Écran Mac", sous-label "En attente…" (gris) |
| `Connecting` | Fond normal, sous-label "Connexion…" (pulsation) |
| `Streaming` | Bord `Shu` (rouge vermillon), sous-label "● Live" (rouge) |
| `Error` | Bord `Kinari` (jaune), sous-label "⚠ Erreur" |

### Comportement au tap
- `Waiting` / `Connecting` : aucun effet (le service est déjà actif)
- `Streaming` : ouvre `AirPlayActivity`
- `Error` : appelle `AirPlayService.restart()`

### Ajout dans TileEditScreen
Entrée "Écran Mac" dans la liste des types de tuiles (catégorie "Système"), icône `Icons.Rounded.Monitor`.

---

## 4. Basculement automatique plein écran

`HomeScreen` observe `airPlayState` via `HomeViewModel`. Un `LaunchedEffect(airPlayState)` démarre `AirPlayActivity` dès que l'état passe à `Streaming`. `AirPlayActivity` se ferme (finish) quand l'état repasse à `Waiting` ou `Error`.

```kotlin
LaunchedEffect(airPlayState) {
    if (airPlayState is AirPlayState.Streaming) {
        ctx.startActivity(Intent(ctx, AirPlayActivity::class.java))
    }
}
```

---

## 5. AirPlayActivity — plein écran

Activity `FLAG_KEEP_SCREEN_ON`, `FLAG_FULLSCREEN`, orientation forcée landscape.

Layout : `SurfaceView` plein écran + HUD `AnimatedVisibility` (auto-masqué après 3s, réapparu au tap) :

```
┌──────────────────────────────────────────────┐
│ ● Fukkatsu · Écran Mac    [disparaît dans 3s] [✕ Quitter] │  ← HUD
│                                              │
│            Surface H.264 plein écran         │
│                                              │
│                                    ▬▬▬       │  ← home pill
└──────────────────────────────────────────────┘
```

- **"✕ Quitter"** : `AirPlayReceiver.disconnect()` → état → `Waiting` → Activity se ferme
- **Bouton Accueil Android** : ne ferme pas la session (l'Activity passe en arrière-plan, le stream continue dans `AirPlayService`)
- **Back gesture** : même comportement que le bouton Accueil

**`AirPlayViewModel`** : observe `AirPlayReceiver.state`, fournit `disconnect()` et la Surface via un `SurfaceHolder.Callback`.

---

## 6. Service lifecycle

`AirPlayService` est démarré :
1. Par `MainActivity.onCreate()` via `startService()`
2. Au démarrage du système via `BootReceiver` (existant ou à créer)

Le service s'arrête uniquement si l'utilisateur retire la tuile AIRPLAY de la grille (implémenté via `PreferencesRepository` — flag `airplay_enabled`).

**Notification ForegroundService** (canal `"airplay"`, importance LOW) :
- `Waiting` : "Écran Mac — en attente de connexion"
- `Streaming` : "Écran Mac — Mac connecté ● Live"

---

## 7. Dépendances à ajouter

```gradle
// app/build.gradle
implementation("javax.jmdns:jmdns:3.5.8")      // mDNS Bonjour
implementation("org.nanohttpd:nanohttpd:2.3.1") // serveur HTTP léger
```

MediaCodec, AES javax.crypto, DatagramSocket : déjà dans le SDK Android.

---

## 8. Fichiers à créer / modifier

| Fichier | Action |
|---|---|
| `airplay/AirPlayState.kt` | Nouveau — sealed class état |
| `airplay/AirPlayReceiver.kt` | Nouveau — orchestrateur + StateFlow |
| `airplay/MdnsAdvertiser.kt` | Nouveau — jmDNS _airplay._tcp |
| `airplay/AirPlayHttpServer.kt` | Nouveau — NanoHTTPD fp-setup + RTSP |
| `airplay/RtpVideoReceiver.kt` | Nouveau — UDP socket + AES decrypt |
| `airplay/H264Renderer.kt` | Nouveau — MediaCodec Surface |
| `airplay/AirPlayService.kt` | Nouveau — ForegroundService |
| `ui/airplay/AirPlayActivity.kt` | Nouveau — plein écran SurfaceView + HUD |
| `ui/airplay/AirPlayViewModel.kt` | Nouveau — observe state, Surface binding |
| `data/tile/TileType.kt` | Modifier — ajouter AIRPLAY |
| `ui/home/MedallionGrid.kt` | Modifier — rendu spécial tuile AIRPLAY |
| `ui/home/HomeScreen.kt` | Modifier — LaunchedEffect basculement auto |
| `ui/home/HomeViewModel.kt` | Modifier — exposer airPlayState |
| `ui/tiles/TileEditScreen.kt` | Modifier — entrée "Écran Mac" |
| `system/BootReceiver.kt` | Créer ou modifier — démarrer AirPlayService |
| `AndroidManifest.xml` | Service + Activity + BootReceiver + permissions |
| `app/build.gradle` | jmDNS + NanoHTTPD |

---

## 9. Permissions AndroidManifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />  <!-- mDNS -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

`CHANGE_WIFI_MULTICAST_STATE` est nécessaire pour que le Wi-Fi ne filtre pas les paquets mDNS multicast.

---

## 10. Contraintes

- Le Portal et le Mac doivent être sur le même réseau Wi-Fi
- AirPlay 1 Screen Mirroring est la cible — pas AirPlay 2 (pas de HAP/HomeKit)
- Le stream vidéo est en H.264 (toujours sur AirPlay 1) — MediaCodec supporte H.264 sur Android 9
- Pas d'audio dans le MVP (le son reste sur le Mac) — le stream audio RTP est ignoré
- Orientation forcée landscape dans `AirPlayActivity` (le Mac envoie en 16:9 landscape)
- La tuile AIRPLAY n'est pas ajoutée par défaut — l'utilisateur l'ajoute manuellement via TileEditScreen
