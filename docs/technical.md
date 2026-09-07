# Fukkatsu — Documentation technique

Référence technique pour les contributeurs et pour la maintenance à long terme. Couvre le script de provisioning, le système d'installation FukkaStore et les adaptations spécifiques au Portal 1ère génération.

---

## Script de provisioning (`scripts/provision-portal.sh`)

Un seul script configure un Portal vierge, de zéro à opérationnel. À lancer avec le Portal branché en USB, débogage activé.

```bash
bash scripts/provision-portal.sh [SERIAL] [OPTIONS]
```

| Option | Effet |
|---|---|
| `--build` | Force la reconstruction de l'APK avant install |
| `--set-launcher` | Définit Fukkatsu comme launcher sans poser la question |
| `--no-launcher` | Laisse le launcher d'origine |
| `SERIAL` | Série ADB du Portal (auto-détecté si un seul appareil) |

### Ce que fait le script, étape par étape

**1. Sélection de l'appareil**
Lit `adb devices` et sélectionne l'appareil automatiquement si un seul est connecté, sinon demande la série.

**2. Désactivation du vérificateur de paquets**
Le Portal n'a pas de GMS fonctionnel. Si Play Protect est actif, il ne peut jamais répondre au serveur de vérification et bloque toute installation avec `install verification failure`. Le script désactive le vérificateur *avant* l'install APK :

```bash
adb shell settings put global package_verifier_enable 0
adb shell settings put global verifier_verify_adb_installs 0
adb shell settings put global package_verifier_user_consent -1
```

Ces réglages s'appliquent aussi aux installs déclenchées par FukkaStore depuis l'app.

**3. Installation de l'APK**

```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

Le flag `-d` permet l'installation d'une version inférieure (downgrade), utile en développement.

**4. Permissions ADB**

| Commande | Rôle |
|---|---|
| `cmd notification allow_dnd $PKG` | Accès Ne Pas Déranger |
| `dpm set-active-admin $PKG/.system.MyDeviceAdminReceiver` | Administrateur d'appareil (extinction écran) |
| `pm grant $PKG android.permission.WRITE_SECURE_SETTINGS` | Contrôle des réglages système sécurisés — indispensable pour l'AccessibilityService d'auto-install sur Portal 1ère gen |
| `pm grant $PKG android.permission.FORCE_STOP_PACKAGES` | Fermeture vraie des apps depuis le multitâche |
| `appops set $PKG WRITE_SETTINGS allow` | Persistance des réglages de luminosité |

**5. Définition du launcher**
Tente `cmd package set-home-activity $PKG/.MainActivity`. Si la commande échoue (certains builds Portal), affiche les instructions manuelles.

---

## FukkaStore — flux d'installation

### Vue d'ensemble

```
Utilisateur tap "Installer"
    → StoreViewModel.install()
    → FukkaAuth.files()       ← gplayapi PurchaseHelper, retourne base + splits
    → ApkDownloader.download() ← télécharge tous les fichiers en cache
    → ApkInstaller.install()
        ├── (API 28) ensureAccessibilityServiceEnabled()
        └── installViaSession()
                ├── filterCompatibleApks()   ← filtre les splits ABI
                ├── PackageInstaller.Session.openSession()
                ├── write de chaque APK dans la session
                └── session.commit(pendingIntent → InstallResultReceiver)
```

### Profil appareil (`assets/fukka_device.properties`)

gplayapi utilise ce profil pour interroger le Play Store. Il est calqué sur un Pixel 3a (Android 9, arm64-v8a + armeabi-v7a) pour maximiser la compatibilité des apps proposées. Le Play Store renvoie les APKs et splits disponibles pour ce profil.

Champ clé : `Platforms=arm64-v8a,armeabi-v7a,armeabi` — gplayapi peut donc retourner un split arm64 même si le device cible ne le supporte pas.

### Filtre ABI (`filterCompatibleApks`)

gplayapi retourne *tous* les splits disponibles pour le profil. Sur Portal 1ère génération (Snapdragon 625, Android 9, 32-bit), installer un split `arm64-v8a` provoque `INSTALL_FAILED_CPU_ABI_INCOMPATIBLE` et l'installation reste bloquée silencieusement.

Le filtre compare le nom de chaque fichier APK aux ABIs réels de l'appareil (`Build.SUPPORTED_ABIS`) :

```kotlin
val supported = Build.SUPPORTED_ABIS.map { it.replace('-', '_') }.toSet()
// Portal 1ère gen : {"armeabi_v7a", "armeabi"}
// Portal 2ème gen : {"arm64_v8a", "armeabi_v7a", "armeabi"}
```

Règle : si le nom du fichier contient un token ABI connu (`arm64_v8a`, `armeabi_v7a`, `x86_64`, `x86`, `armeabi`) ET que cet ABI n'est pas dans `supported`, le fichier est écarté. Les splits sans token ABI (base, langue, densité) passent toujours.

---

## Adaptations Portal 1ère génération

Le Portal Plus 1ère génération (Android 9 / API 28) présente deux comportements non-standard qui nécessitent des contournements spécifiques.

### Overlay blanc sur les activités tierces

Le `StatusBarManagerService` du Portal affiche un overlay blanc opaque sur toute activité dont il ne connaît pas l'état de visibilité. Cela concerne notamment `PackageInstallerActivity` (fenêtre de confirmation d'installation). Les logs Android montrent :

```
activity not set visibility yet, use default
notifyBar(pkg, activity, true, false, false)
```

**Constat important** : l'overlay est visuel seulement. Les événements tactiles passent à travers et atteignent la fenêtre sous-jacente. L'UI de confirmation est rendue correctement (confirmé par `uiautomator dump` qui liste les boutons à leurs coordonnées exactes).

### Solution : InstallAccessibilityService

`store/InstallAccessibilityService` surveille les événements de fenêtre de `com.android.packageinstaller` et clique automatiquement sur le bouton `ok_button` (« Installer »).

```kotlin
// Déclenché sur typeWindowStateChanged / typeWindowContentChanged
// Cherche : com.android.packageinstaller:id/ok_button
// Action : ACTION_CLICK si isEnabled && isClickable
```

Le service est activé programmatiquement dans `ApkInstaller.ensureAccessibilityServiceEnabled()` via `WRITE_SECURE_SETTINGS` (accordé par le script de provisioning) :

```kotlin
Settings.Secure.putString(
    contentResolver,
    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    "$currentList:com.cyprienbrisset.myportal/.store.InstallAccessibilityService"
)
Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
```

Sans `WRITE_SECURE_SETTINGS`, le service doit être activé manuellement dans Réglages → Accessibilité.

La configuration du service (`res/xml/accessibility_service.xml`) est limitée à `com.android.packageinstaller` pour éviter tout impact sur d'autres activités.

### Flux complet sur Portal 1ère gen

1. `ensureAccessibilityServiceEnabled()` active le service via `WRITE_SECURE_SETTINGS`
2. `installViaSession()` avec les splits ABI filtrés ouvre une session `PackageInstaller`
3. Le système lance `PackageInstallerActivity` → overlay blanc Portal, boutons invisibles
4. `InstallAccessibilityService` détecte la fenêtre et clique `ok_button`
5. Le système affiche `InstallInstalling` (visible sur Portal car non bloqué par l'overlay)
6. L'installation se termine, `InstallResultReceiver` reçoit le broadcast de résultat
7. L'UI FukkaStore passe à l'état `INSTALLED`

---

## Architecture — fichiers clés

| Fichier | Rôle |
|---|---|
| `store/ApkInstaller.kt` | Orchestration : choix du chemin d'install, filtre ABI, session PackageInstaller |
| `store/ApkDownloader.kt` | Téléchargement des fichiers APK avec progression |
| `store/FukkaAuth.kt` | Authentification Google (AC2DM → AAS token), appels gplayapi |
| `store/InstallAccessibilityService.kt` | Auto-click bouton Install sur Portal 1ère gen |
| `store/InstallResultReceiver.kt` | Réception du résultat de session PackageInstaller via broadcast |
| `store/InstallTrampolineActivity.kt` | Activité transparente (historique, non utilisée pour les nouvelles installs) |
| `system/MyDeviceAdminReceiver.kt` | Device Admin pour extinction d'écran |
| `assets/fukka_device.properties` | Profil appareil Pixel 3a pour gplayapi |
| `res/xml/accessibility_service.xml` | Config AccessibilityService, scope `com.android.packageinstaller` |
| `scripts/provision-portal.sh` | Provisioning complet d'un Portal vierge via ADB |

---

## Environnement de build

Android Studio intègre un JDK (JBR). Aucun `java` requis sur le PATH système :

```bash
JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

ADB est attendu dans `~/Library/Android/sdk/platform-tools/adb`. Surchargeable via la variable d'env `ADB`.
