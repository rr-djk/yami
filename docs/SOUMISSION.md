# Yamy — soumission « Best Use of Gemma 4 »

**Dis-lui tout. Rien ne quitte ton téléphone.**

## L'idée
Yamy est un compagnon conversationnel Android qui tourne **entièrement sur le téléphone**, grâce à **Gemma 4 E2B** (poids ouverts, ~2,6 Go) exécuté avec LiteRT-LM. On peut lui confier des choses personnelles : l'app n'a **aucune permission Internet**, ce qui se vérifie dans les infos de l'app, en mode avion, et dans l'écran « Vie privée » qui relit l'état réel de l'app.

## Pourquoi Gemma 4, et pourquoi en local
- **Multimodal sur l'appareil** : on peut montrer une photo à Yamy, analysée localement (aucune image n'est conservée ni envoyée).
- **Petit modèle, vraie conversation** : sur Pixel 10a, premier mot en ~1,7 s et ~15 morceaux/s en CPU (`docs/MESURES.md`).
- **Un modèle ouvert permet une promesse invérifiable ailleurs, vérifiable ici** : la confidentialité est une propriété de l'architecture, pas une politique.

## Ce qui est construit
- Chat en streaming, ton réglable par un `PERSONA.md`.
- **Skills créés par l'utilisateur** : on décrit une tâche à Yamy, il rédige un `SKILL.md` (relu et modifiable avant enregistrement), puis le charge de lui-même quand la demande correspond (outil `loadSkill`).
- **Confidentialité** : base chiffrée (SQLCipher, clé dans le Keystore), skills chiffrés, verrou biométrique, captures bloquées, sauvegarde cloud désactivée, « Tout effacer ».
- **Sécurité émotionnelle en code** : un détecteur de détresse (français/anglais) court-circuite le modèle et répond avec une orientation vers une aide humaine ; Yamy dit toujours qu'il est une IA qui peut se tromper.
- Interface rétro (Macondo), accessibilité vérifiée par mesure des contrastes.

## Ce que nous avons mesuré (honnêtement)
- Perf : `docs/MESURES.md` (CPU vs GPU, charge soutenue 5 min : +5 °C, jamais > 35,1 °C, en charge).
- Qualité : `docs/BANC_TEST.md` — 18/21 puis 21/21 réponses acceptables après corrections, **notées par l'IA qui a écrit le code** (à relire), un seul tirage.
- Sécurité : audit indépendant de la couche confidentialité, correctifs appliqués ; risques restants listés dans `PLAN.md`.

## Connecteurs MCP (v0.3)
Yamy peut, si la personne l'active, se brancher sur des serveurs MCP. Premier connecteur : [gmail-mcp-server](https://github.com/rr-djk/gmail-mcp-server), porté dans l'app avec le même protocole JSON-RPC. **Hors ligne par défaut** : un switch visible passe en mode Connecté, Gemma ne fait que proposer un email, et la personne confirme chaque envoi.

## Limites assumées
- Yamy n'est pas un professionnel de santé ; le détecteur par mots-clés peut rater des formulations indirectes.
- Numéros d'aide à adapter au pays (3114, 15, 3919, 17, 9-8-8, 112 par défaut).
- « Tout effacer » n'est pas un effacement cryptographique ; le modèle n'est pas vérifié par empreinte ; le build de démo est signé avec une clé de debug.
- Le modèle est copié par `adb push` (l'app ne télécharge rien).

## Reste à faire
Test utilisateur large, plus de langues, mémoire à long terme, effacement cryptographique, signature de production.
