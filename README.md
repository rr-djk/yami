# Yamy

Compagnon IA Android **100 % local** : Gemma-4-E2B-it exécuté sur l'appareil via LiteRT-LM. Aucune permission `INTERNET`, aucun réseau. Cible : Pixel 10a (hackathon *Best Use of Gemma 4*).

Yamy est une IA, pas un professionnel de santé. La détresse est gérée dans le code, pas seulement dans le prompt.

## Prérequis

- JDK 21, Android SDK (compileSdk 36, minSdk 31), `adb`
- Pixel avec débogage USB activé
- Le modèle `gemma-4-E2B-it.litertlm` (~2,6 Go), **jamais commité** ni téléchargé par l'app

## Démarrer

```sh
source scripts/env.sh            # JAVA_HOME, ANDROID_HOME, PATH (adb, sdkmanager)
./gradlew installDebug           # build + installation sur l'appareil
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/app.yamy/files/
```

Sans le modèle, l'app refuse de lancer le chat (`ModelLocator`). Test d'acceptation : conversation complète en mode avion.

## Tests

```sh
./gradlew :app:testDebugUnitTest                         # JVM : détresse, skills
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class app.yamy.BenchTest \
  app.yamy.test/androidx.test.runner.AndroidJUnitRunner  # banc de sécurité sur appareil
```

`SustainedLoadTest` mesure la chaleur et le débit en charge soutenue.

## Architecture

Kotlin 2.4.0 + Jetpack Compose (Material 3), package `app.yamy`.

| Package | Rôle |
|---|---|
| `engine/` | `ChatEngine` (interface) → `LiteRtChatEngine`, `ModelLocator`, `SkillToolSet` (outil `loadSkill`) |
| `chat/` | Écran, `ChatViewModel`, `ConversationRepository` |
| `data/` | Room + SQLCipher (base chiffrée) |
| `skills/` | Skills utilisateur (`SKILL.md`), création par `SkillCreator`, stockage chiffré |
| `safety/` | `DistressDetector` (mots-clés), `TurnPlanner`, `CrisisResponder`, `Helpline` |
| `privacy/` | `AppLock` (biométrie), `KeystoreSecretBox`, `DataWiper` (« Tout effacer »), `PrivacyAudit` |
| `persona/` | `PERSONA.md` éditable (ton uniquement) |
| `vision/` | Image (galerie / appareil photo) → modèle |
| `ui/` | Thème rétro (Macondo, palette bleu-violet) et composants |

Le runtime n'est jamais référencé hors de `engine/` : l'UI et le chat dépendent de `ChatEngine`.

## Décisions à connaître

- **Backend CPU par défaut** : chargement 0,8 s contre 51,6 s en GPU, débit équivalent, moins de mémoire.
- **Kotlin 2.4.0** est imposé par LiteRT-LM 0.18.0 : ne pas le monter seul.
- **Pas de mémoire locale** pour l'instant ; seul l'historique Room est conservé.
- **Skills** : texte uniquement, chargés à la demande par le modèle. Pas de repli `/skill`.
- **Vision** : fidélité image de l'E2B limitée sur Android (LiteRT-LM#3851).

## Vie privée

`allowBackup=false`, `FLAG_SECURE`, base SQLCipher dont la clé est protégée par le Keystore, verrou biométrique, aucune notification. L'écran « Vie privée » audite ces points en direct. Risques résiduels (effacement non cryptographique, build debug lisible par adb, modèle non vérifié par empreinte) : `PLAN.md` §6.

## Workflow

Une étape = une branche `step/NN-nom`, fusionnée `--no-ff` dans `main`. Commits en français (Conventional Commits). Pas de `push` sans demande.

## Documentation

- `PLAN.md` : étapes, décisions, checklists (source de vérité)
- `docs/MESURES.md` : performances du Pixel 10a
- `docs/BANC_TEST.md` : banc de sécurité (21 messages) et ses limites
- `AGENTS.md` : consignes pour les agents de code
