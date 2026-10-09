# Mesures de performance — Pixel 10a, Gemma-4-E2B-it (LiteRT-LM 0.18.0)

Prompt : « Salut Yamy, j'ai eu une journée longue. Peux-tu me remonter le moral en quelques phrases ? »
Une exécution par backend, téléphone en charge (60 %), batterie à 28,9 °C. « Morceau » = émission du flux, assimilé à un token (approximation non vérifiée).

| | CPU | GPU |
|---|---|---|
| Chargement | 0,8 s | 51,6 s (premier chargement, compilation des shaders) |
| Premier morceau (TTFT) | 1,66 s | 0,91 s |
| Génération | 68 morceaux / 269 car. en 5,9 s | 60 morceaux / 251 car. en 5,1 s |
| Débit (hors 1er morceau) | ~15,7 morceaux/s (63 car./s) | ~13,9 morceaux/s (59 car./s) |
| Mémoire native | 357 Mo | 558 Mo |
| Température batterie | 28,9 → 28,9 °C | 28,9 → 28,9 °C |

Captures : `mesures/etape3-cpu.png`, `mesures/etape3-gpu.png`.

**Décision (étape 3) : CPU par défaut** — chargement quasi instantané, moins de mémoire, débit équivalent, TTFT sous le seuil de 3 s.

## À compléter
- Test de chaleur soutenu (≥ 5 min de conversation).
- Consommation de batterie sur une session.
- Mesures avec image (étape 5).

## Charge soutenue (étape 10) — 5 min de génération continue, CPU
`SustainedLoadTest` (fichier brut : `sustained-out.tsv`). Pixel 10a **branché en USB (en charge, batterie à 100 %)** : la charge ajoute de la chaleur, la température est donc un majorant d'un usage sur batterie.

| Temps | Température batterie | Débit (morceaux/s) | Réponses cumulées |
|---|---|---|---|
| 7 s | 29,8 °C | 7,3 | 1 |
| 70 s | 30,8 °C | 12,3 | 10 |
| 150 s | 33,8 °C | 9,3 | 19 |
| 242 s | 34,8 °C | 6,3 | 28 |
| 303 s | 35,1 °C | 10,5 | 34 |

- Température : +5,3 °C en 5 minutes, jamais au-delà de 35,1 °C (tiède, pas brûlant).
- Débit : ~12-13 morceaux/s au début, entre 6 et 10 ensuite (baisse probable due à la chaleur, non prouvée), sans interruption ni plantage sur 34 réponses.
- Non mesuré : consommation de batterie (téléphone en charge), température du processeur (seule la batterie est lisible sans permission).
