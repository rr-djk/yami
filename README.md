# Yamy

Compagnon IA Android **hors ligne par défaut** : Gemma-4-E2B-it exécuté sur l'appareil via LiteRT-LM. Cible : Pixel 10a.

Depuis la v0.3, l'app déclare la permission `INTERNET`, utilisée **uniquement** par les connecteurs MCP (Gmail) et **seulement** en mode Connecté : switch dans Vie privée, **Hors ligne par défaut**, chaque email relu et confirmé par la personne. Le modèle, les discussions, la mémoire et la voix ne quittent jamais le téléphone.

## Connecteurs MCP

- Client MCP générique (`mcp/McpClient.kt`, JSON-RPC 2.0 : `initialize`, `tools/list`, `tools/call`).
- Serveur [rr-djk/gmail-mcp-server](https://github.com/rr-djk/gmail-mcp-server) porté dans l'app (`mcp/gmail/GmailMcpServer.kt`) : même protocole et même outil `send_email`, plus un destinataire libre (`to`). Android ne lance pas un `java -jar` en stdio, d'où le transport en mémoire (`InProcessTransport`).
- Gemma ne fait que **proposer** un email (outil `preparerEmail`) ; l'envoi part du bouton Envoyer, via `NetworkGate`, unique point de passage vers le réseau, fermé en mode Hors ligne.
- Identifiants : adresse Gmail + mot de passe d'application, chiffrés (Keystore), effacés par « Tout effacer ».

## Contexte

Projet réalisé dans le cadre du **Hacktoberfest 26 de l'UQAM** ([Hacktoberfest Hack Day Montréal x AGEEI](https://www.mlh.com/events/hacktoberfest-hack-day-montreal-x-ageei/challenges/01a03b87-be22-9180-5e2d-b3beeb66666d)), pour le défi **Best Use of Gemma 4** (Google DeepMind) : construire un outil avec un modèle ouvert et léger, multimodal (texte et images).

Yamy y répond ainsi : Gemma 4 E2B tourne sur le téléphone (poids ouverts, aucune API distante), comprend les photos qu'on lui montre, et sert d'outil ciblé pour parler en confiance. Texte de soumission : [`docs/SOUMISSION.md`](docs/SOUMISSION.md).

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

## Licence

Code source sous **licence Apache 2.0** : voir [`LICENSE`](LICENSE) et [`NOTICE`](NOTICE).

- **Modèle utilisé** : [Gemma-4-E2B-it](https://huggingface.co/google/gemma-4-E2B-it) de Google DeepMind, distribué sous licence Apache 2.0 ([fiche du modèle](https://ai.google.dev/gemma/docs/core/model_card_4), version LiteRT-LM : [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)). Il n'est pas inclus dans ce dépôt : il se copie sur le téléphone avec `adb push`.
- **Polices** : Macondo et JetBrains Mono, sous SIL Open Font License 1.1.
