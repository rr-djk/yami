# PLAN.md — Yamy, un ami IA hors ligne par défaut (Android)

Hackathon : **Best Use of Gemma 4** (Google DeepMind). Durée : **8 h**.
Appareil de test : **Pixel 10a** (USB). Développeur débutant Android, assisté par IA. Build en **ligne de commande** (pas d'Android Studio).

> Les éléments marqués **[À VÉRIFIER]** sont des hypothèses non confirmées. Il n'en reste que deux : performances réelles sur le Pixel 10a, et règlement vs « Gemini API ».

---

## 1. Vision

Yamy est un compagnon conversationnel qui tourne **entièrement sur le téléphone**. Rien ne quitte l'appareil.

**Promesse vérifiable :**
1. Hors ligne par défaut : aucune donnée ne sort tant que le switch est sur Hors ligne (v0.1-v0.2 : aucune permission INTERNET ; v0.3 : permission déclarée pour les seuls connecteurs MCP).
2. Démo **en mode avion**.
3. Sauvegarde cloud désactivée, captures bloquées, verrouillage biométrique.
4. Bouton « Tout effacer ».

**Slogan :** « Dis-lui tout. Rien ne quitte ton téléphone. »

**Cœur du projet :** multimodal (texte + image avec Gemma 4) et **skills créés par l'utilisateur** (tâches réutilisables dictées à Yamy).

**Hors périmètre :** sync, comptes, multi-appareils, thèmes. Public volontairement ouvert : le ton se règle via `PERSONA.md`.

---

## 2. Décisions techniques (verrouillées)

| Sujet | Choix |
|---|---|
| Langage / UI | Kotlin + Jetpack Compose, Material 3 (skill `.claude/skills/android-design`) |
| Modèle | **Gemma-4-E2B-it** (~2,6 Go, `.litertlm`). Pas d'E4B. Repli : quantification plus légère de l'E2B |
| Runtime | **LiteRT-LM** (Google AI Edge), même stack que [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) |
| Installation du modèle | `adb push` (jamais téléchargé par l'app → pas de permission INTERNET) |
| Exécution | Locale pure, aucun mode API |
| Ton | `PERSONA.md` éditable (ton/personnalité uniquement) |
| Sécurité de détresse | **En code** (`DistressDetector`), jamais seulement dans un .md |
| Skills | Dossier `SKILL.md` (frontmatter `name` + `description`, corps d'instructions), texte seul, création par dictée + relecture, chargement progressif, outil `load_skill` (repli : commande `/skill`) |
| Stockage | Room + chiffrement Android Keystore |
| Verrouillage | BiometricPrompt |

**Architecture (SOLID, MVVM)** : packages `engine/ chat/ skills/ safety/ privacy/ ui/`. Interfaces : `ChatEngine` (impl. `LiteRtChatEngine`), `SkillRepository`, `MemoryRepository`, `DistressDetector`, `PersonaProvider`. Injection par constructeur.

**Faits vérifiés :** Gemma 4 (avril 2026, Apache 2.0), audio et image sur E2B/E4B ; E2B `.litertlm` ≈ 2,58 Go, ~1,1 Go RAM ; bug connu de fidélité image/audio sur Android ([LiteRT-LM #3851](https://github.com/google-ai-edge/LiteRT-LM/issues/3851)) → tester l'image tôt. Licence de Gallery à vérifier avant toute copie de code.

---

## 3. Méthode de travail

- **1 étape = 1 branche `step/NN-nom`**, merge `--no-ff` dans `main` en fin d'étape. Commits Conventional Commits, en français. Pas de push sans demande.
- Revue (`code-reviewer`) à la fin de chaque étape ; audit sécurité à l'étape 7.
- Arrêt seulement pour : décision ambiguë, test physique sur le Pixel, blocage externe.

---

## 4. Étapes

| # | Branche | Durée | Contenu | Critère de sortie |
|---|---|---|---|---|
| 0 | `step/00-cadrage` | 0:00–0:30 | .gitignore, PLAN.md, AGENTS.md | Plan versionné. Question « Gemini API » posée aux organisateurs |
| 1 | `step/01-toolchain` | 0:30–1:15 | JDK, SDK Android CLI, adb | `adb devices` voit le Pixel |
| 2 | `step/02-scaffold` | 1:15–2:00 | Projet Compose, thème M3 | « Yamy » affiché sur le Pixel |
| 3 | `step/03-engine` | 2:00–3:15 | `ChatEngine` + LiteRT-LM, mesures | Phrase générée ; tableau (chargement, TTFT, tok/s, mémoire, chaleur) ; **décision modèle** |
| 4 | `step/04-chat` | 3:15–4:30 | Chat streaming, `PERSONA.md`, Room | 10 échanges en mode avion |
| 5 | `step/05-vision` | 4:30–5:30 | Image → E2B, cas d'usage | Image comprise de façon fiable |
| 6 | `step/06-skills` | 5:30–6:30 | `SKILL.md`, `load_skill`, dictée + relecture | Un skill dicté puis utilisé |
| 7 | `step/07-design-retro` | (ajout) | Thème rétro (skill `retro-style`) : Macondo + JetBrains Mono embarquées, palette bleu/violet, cartes à ombre dure | Rendu à valider sur le Pixel |
| 8 | `step/08-privacy` | 6:30–7:00 | `allowBackup=false`, `FLAG_SECURE`, Keystore, biométrie, « Tout effacer » | Checklist §6 cochée |
| 9 | `step/09-safety-eval` | 7:00–7:40 | `DistressDetector`, banc 18+ messages, tests JVM | Score X/18 consigné |
| 10 | `step/10-demo` | 7:40–8:00 | README, vidéo plan B, soumission | Livrables §8 (README, mesures, banc, soumission faits ; vidéo à enregistrer par l'équipe) |

### Points de décision
| Moment | Question | Si non |
|---|---|---|
| Étape 2 | Hello World sur le Pixel ? | Partir de l'app Gallery |
| Étape 3 | E2B assez rapide et froid (premier mot < ~3 s) ? | Quantification plus légère, sinon réduire le contexte |
| Étape 5 | Image fiable ? | Réduire à un cas simple ; bug #3851 documenté |
| Étape 6 | L'E2B appelle `load_skill` de façon fiable ? | Commande `/skill` explicite |

**À couper en premier :** dictée (→ formulaire), mémoire élaborée, biométrie avancée, audio.
**Jamais coupé :** chat local, preuve de confidentialité, sécurité de base, mesures chiffrées.

---

## 5. Risques

| Risque | Réponse |
|---|---|
| Règlement : « Gemini API » attendue ? **[À VÉRIFIER]** | Question aux organisateurs dès l'étape 0 ; le local reste l'argument (poids ouverts) |
| Performances du E2B sur le Pixel 10a **[À VÉRIFIER]** | Mesures à l'étape 3 avant de bâtir dessus |
| Premier projet Android | Étapes 1-2 courtes ; plan B : cloner Gallery |
| « Local » ≠ confidentiel (sauvegardes, clavier, captures) | Checklist §6 |
| Réponse inadaptée à un utilisateur en détresse | `DistressDetector` en code + banc de test |
| E2B génère de mauvais `SKILL.md` | Gabarit imposé + écran de relecture |
| Panne en démo | Vidéo plan B + captures |
| Sur-promesse (« thérapeute ») | Yamy est une IA, pas un professionnel de santé |

---

## 6. Checklist confidentialité

- [x] Hors ligne par défaut ; réseau réservé aux connecteurs MCP, en mode Connecté, avec confirmation de chaque envoi.
- [ ] Fonctionne en mode avion (aucun accès réseau dans le code ; test manuel à faire).
- [x] `allowBackup=false`.
- [x] `FLAG_SECURE`.
- [x] Données (messages, skills) chiffrées, clé dans le Keystore.
- [x] Verrouillage biométrique.
- [ ] Notifications sans contenu.
- [ ] « Tout effacer » vérifié sur le Pixel (implémenté et audité ; test manuel à faire).
- [x] Aucune bibliothèque d'analytique ni de journalisation distante.
- [ ] Limite assumée : clavier tiers et téléphone déverrouillé hors du contrôle de l'app.

### Risques restants connus (audit de l'étape 8)
- **Effacement non cryptographique** : « Tout effacer » supprime les lignes, mais la clé du Keystore et le fichier de base restent ; des pages libérées pourraient être récupérables par quelqu'un qui exécute du code en tant qu'app (build debug, root).
- **Verrou** : sans écran de verrouillage sur le téléphone, l'app s'ouvre sans verrou (signalé dans « Vie privée »).
- **Modèle** : non vérifié par empreinte (SHA-256) ; quelqu'un avec adb peut le remplacer.
- **Cache LiteRT** (~1 Go) non vidé par « Tout effacer » (contenu dérivé du modèle, à confirmer).
- **Clavier / texte non envoyé** : `rememberSaveable` garde le texte en cours de saisie dans l'état système.
- **Build debug** : débogable, donc lisible par adb ; la démo doit utiliser un build de production (le contrôle « Build de production » l'indique).
- **Appareil photo** : la photo transite par l'application d'appareil photo du téléphone (copie temporaire supprimée par Yamy ensuite).

## 7. Banc de test

Trois familles (≈ 6 messages chacune) + quelques cas image :

| Famille | Comportement attendu |
|---|---|
| Quotidien banal | Chaleureux, court, non intrusif |
| Émotion forte | Écoute, pas de diagnostic, pas de fausse promesse |
| Détresse | Orientation vers une aide humaine, ton sobre |

Tester aussi les tentatives de contournement des consignes. Afficher « X / N acceptables, voici les cas faibles ».

## 8. Livrables

- [x] Dépôt + instructions de build (CLI) et de placement du modèle.
- [x] Tableau de mesures Pixel 10a.
- [x] Banc de test et résultats.
- [ ] Vidéo de démo (plan B).
- [x] Texte de soumission : usage de Gemma 4, pourquoi le local, limites.

**Scénario de démo (3-5 min) :** permissions vides → mode avion → conversation + photo → création d'un skill par dictée → « Tout effacer » → message de détresse → tableau de mesures → « Ne nous croyez pas, vérifiez ».

## 9. Questions ouvertes

- Règlement : le local avec poids ouverts est-il accepté sans appel à la Gemini API ?
- Cas d'usage image principal (photo de journée, capture de message reçu, …).
- Numéro d'écoute adapté au pays de la démo.
- Nom définitif (Yamy / « yami » dans le dépôt) : vérifier Play Store, GitHub, domaine.

## 10. Journal de décisions

| Étape | Décision | Raison |
|---|---|---|
| 0 | E2B uniquement, local pur, Gemma 3 retiré | Éligibilité « Gemma 4 », promesse de confidentialité |
| 0 | Skills texte uniquement | Pas de permission supplémentaire, faisable en 8 h |
| 4 | Chat validé sur le Pixel : streaming fluide, température OK, historique persistant | Test utilisateur (mode avion à confirmer à l'étape 7) |
| 5 | Vision validée sur le Pixel (photo appareil + galerie, description correcte avec E2B/CPU) | Test utilisateur ; cas d'usage de démo à choisir |
| 6 | Skills considérés validés (non testés sur le Pixel : à re-tester : dictée, `loadSkill` auto, repli `/skill` si l'E2B n'appelle pas l'outil) | Demande utilisateur, test différé |
| 7 | Style rétro, thème clair uniquement, sans couleurs dynamiques. Bleu/vert/orange en fond seulement (contraste < 4,5:1 en texte sur blanc), violet en grands titres/contours | Skill retro-style + accessibilité prioritaire ; android-design (couleurs dynamiques) écarté à la demande de l'utilisateur |
| 11 | Discussions thématiques + barre de navigation ; migration Room 2→3 vérifiée sur la vraie base (14 messages conservés) | Demande utilisateur (sessions par sujet) |
| 12 | Micro : transcription Gemma pendant la parole, puis réponse ; voix TTS hors ligne seulement | Mesures `docs/MESURES.md` ; détresse vérifiée sur le texte ; aucune donnée envoyée en ligne |
| 13 | Chaînes d'agents orchestrées en code (Le Conseil, Révision, Entretien) | Petit modèle : enchaînement déterministe plus fiable ; ~30 s par chaîne |
| 14 | Mémoire façon Obsidian (index + notes reliées chiffrées, Scribe annulable, outil lireNote) | Schéma « LLM Wiki » de Karpathy, déclenché à la demande (« 📝 Retenir ») |
| 15 | Connecteurs MCP : serveur Gmail porté dans l'app + switch logiciel Hors ligne/Connecté (choix utilisateur : une seule app) | Android ne lance pas de serveur stdio Java ; promesse reformulée honnêtement (« hors ligne par défaut ») |
| 3 | Backend CPU par défaut ; Kotlin 2.4.0 (imposé par LiteRT-LM 0.18.0) | Chargement 0,8 s vs 51,6 s, débit équivalent, moins de mémoire (voir `docs/MESURES.md`) |
