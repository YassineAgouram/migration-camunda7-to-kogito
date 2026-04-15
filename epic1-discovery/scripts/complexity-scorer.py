#!/usr/bin/env python3
"""
complexity-scorer.py
Calcule le score de complexité de migration Camunda → Kogito
par processus, à partir du CSV d'inventaire.

Usage:
    python3 complexity-scorer.py --input ../outputs/inventory.csv
    python3 complexity-scorer.py --input ../outputs/inventory.csv --output heatmap.md
"""

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

# ─── Grille de scoring ────────────────────────────────────────────────────────

SCORING = {
    "scripts": {
        0: (0, "Aucun script"),
        1: (5, "Scripts simples (< 10 lignes, stateless)"),
        2: (15, "Scripts complexes ou avec injection de beans"),
    },
    "delegates": {
        0: (0, "Aucun delegate"),
        1: (5, "Delegates simples (CRUD, appel REST)"),
        2: (15, "Delegates avec état, transactions, retry"),
    },
    "external_tasks": {
        0: (0, "Aucune external task"),
        1: (5, "1-3 topics"),
        2: (10, "> 3 topics ou polling custom"),
    },
    "user_tasks": {
        0: (0, "Aucune tâche humaine"),
        1: (5, "Formulaires Camunda natifs"),
        2: (15, "Formulaires externes (Angular, React)"),
    },
    "dmn": {
        0: (0, "Aucun DMN"),
        1: (3, "DMN simple (1 table)"),
        2: (10, "DMN complexes ou chaînés"),
    },
    "async_boundaries": {
        0: (0, "Aucun"),
        1: (5, "< 3 async boundaries"),
        2: (15, "≥ 3 async ou compensation BPMN"),
    },
    "connectors": {
        0: (0, "Aucun connector"),
        1: (5, "Connectors standards (REST, SOAP)"),
        2: (15, "Connectors custom ou plugins propriétaires"),
    },
    "serialization": {
        0: (0, "Variables simples (primitives, JSON)"),
        1: (10, "Sérialisation Java (Serializable)"),
        2: (15, "Types complexes, sérialisation custom"),
    },
}

LOT_THRESHOLDS = [
    (0, 20, "🟢 Simple", "Lot 1 — Pilote (quick wins)"),
    (21, 45, "🟡 Modéré", "Lot 2 — Migration standard"),
    (46, 70, "🟠 Complexe", "Lot 3 — Refonte partielle"),
    (71, 100, "🔴 Critique", "Lot 4 — Refonte complète ou report"),
]


def classify(score: int):
    for low, high, level, lot in LOT_THRESHOLDS:
        if low <= score <= high:
            return level, lot
    return "🔴 Critique", "Lot 4"


def score_process(assets: dict) -> dict:
    """
    assets = {
        'bpmn_name': str,
        'has_scripts': bool,
        'script_complexity': int (0|1|2),
        'delegate_count': int,
        'delegate_complexity': int (0|1|2),
        'external_task_topics': list[str],
        'has_user_tasks': bool,
        'user_task_type': int (0|1|2),
        'dmn_count': int,
        'dmn_complexity': int (0|1|2),
        'async_count': int,
        'has_connectors': bool,
        'connector_type': int (0|1|2),
        'serialization_type': int (0|1|2),
    }
    """
    details = {}

    # Scripts
    sc = assets.get("script_complexity", 0)
    details["scripts"] = SCORING["scripts"][sc]

    # Delegates
    dc = assets.get("delegate_complexity", 0)
    details["delegates"] = SCORING["delegates"][dc]

    # External tasks
    topics = assets.get("external_task_topics", [])
    if len(topics) == 0:
        etc = 0
    elif len(topics) <= 3:
        etc = 1
    else:
        etc = 2
    details["external_tasks"] = SCORING["external_tasks"][etc]

    # User tasks
    utc = assets.get("user_task_type", 0)
    details["user_tasks"] = SCORING["user_tasks"][utc]

    # DMN
    dmn = assets.get("dmn_count", 0)
    dmn_cx = assets.get("dmn_complexity", 0)
    details["dmn"] = SCORING["dmn"][dmn_cx if dmn > 0 else 0]

    # Async
    async_c = assets.get("async_count", 0)
    if async_c == 0:
        ac = 0
    elif async_c < 3:
        ac = 1
    else:
        ac = 2
    details["async_boundaries"] = SCORING["async_boundaries"][ac]

    # Connectors
    cc = assets.get("connector_type", 0)
    details["connectors"] = SCORING["connectors"][cc]

    # Serialization
    ser = assets.get("serialization_type", 0)
    details["serialization"] = SCORING["serialization"][ser]

    total = sum(v[0] for v in details.values())
    level, lot = classify(total)

    return {
        "total_score": total,
        "level": level,
        "lot": lot,
        "details": details,
        "blockers": [k for k, v in details.items() if v[0] >= 15],
        "quick_win": total <= 15,
    }


def generate_heatmap(results: list) -> str:
    lots = defaultdict(list)
    for r in results:
        lots[r["lot"]].append(r)

    lines = ["# HEATMAP DE COMPLEXITÉ — MIGRATION CAMUNDA 7 → KOGITO", ""]

    for _, _, level, lot in LOT_THRESHOLDS:
        if lot in lots:
            lines.append(f"## {level} — {lot}")
            for r in sorted(lots[lot], key=lambda x: x["total_score"]):
                blockers = f"  ⛔ Blockers: {', '.join(r['blockers'])}" if r["blockers"] else ""
                qw = "  ⚡ QUICK WIN" if r["quick_win"] else ""
                lines.append(f"- **{r['process_id']}** [Score: {r['total_score']}]{qw}{blockers}")
            lines.append("")

    all_blockers = [r for r in results if r["blockers"]]
    if all_blockers:
        lines.append("## 🚫 Blockers identifiés")
        for r in all_blockers:
            for b in r["blockers"]:
                lines.append(f"- `{r['process_id']}` → **{b}** (score partiel: {r['details'][b][0]})")
        lines.append("")

    quick_wins = [r for r in results if r["quick_win"]]
    if quick_wins:
        lines.append("## ⚡ Quick Wins (migration en 1 sprint)")
        for r in quick_wins:
            lines.append(f"- `{r['process_id']}` [Score: {r['total_score']}]")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Camunda → Kogito complexity scorer")
    parser.add_argument("--input", required=True, help="CSV d'inventaire (depuis inventory-bpmn.sh)")
    parser.add_argument("--output", default=None, help="Fichier de sortie heatmap (markdown)")
    parser.add_argument("--json", action="store_true", help="Sortie JSON détaillée")
    args = parser.parse_args()

    # Exemple: lire le CSV et simuler le scoring
    # En production, le CSV contiendrait les colonnes de scoring
    results = []
    input_path = Path(args.input)

    if not input_path.exists():
        print(f"⚠️  Fichier non trouvé: {args.input}")
        print("Utilisation du dataset de démonstration...")
        # Dataset de démonstration
        demo_processes = [
            {"process_id": "simple-approval", "script_complexity": 0, "delegate_complexity": 0,
             "external_task_topics": [], "user_task_type": 1, "dmn_count": 0, "dmn_complexity": 0,
             "async_count": 0, "connector_type": 0, "serialization_type": 0},
            {"process_id": "order-process", "script_complexity": 1, "delegate_complexity": 1,
             "external_task_topics": [], "user_task_type": 1, "dmn_count": 1, "dmn_complexity": 1,
             "async_count": 2, "connector_type": 0, "serialization_type": 0},
            {"process_id": "credit-check", "script_complexity": 1, "delegate_complexity": 2,
             "external_task_topics": ["check-credit", "notify"], "user_task_type": 0, "dmn_count": 2,
             "dmn_complexity": 2, "async_count": 2, "connector_type": 0, "serialization_type": 0},
            {"process_id": "legacy-claim", "script_complexity": 2, "delegate_complexity": 2,
             "external_task_topics": ["claim-eval", "fraud-check", "notify", "archive"],
             "user_task_type": 2, "dmn_count": 3, "dmn_complexity": 2, "async_count": 4,
             "connector_type": 2, "serialization_type": 1},
        ]
        for p in demo_processes:
            score = score_process(p)
            score["process_id"] = p["process_id"]
            results.append(score)
    else:
        with open(input_path) as f:
            reader = csv.DictReader(f)
            for row in reader:
                score = score_process(row)
                score["process_id"] = row.get("process_id", "unknown")
                results.append(score)

    # Affichage console
    print("\n" + "=" * 60)
    print("RÉSULTATS DE SCORING")
    print("=" * 60)
    for r in sorted(results, key=lambda x: x["total_score"]):
        print(f"\n{r['level']} [{r['total_score']:3d}/100] {r['process_id']}")
        print(f"  → {r['lot']}")
        if r["blockers"]:
            print(f"  ⛔ Blockers: {', '.join(r['blockers'])}")
        if r["quick_win"]:
            print("  ⚡ QUICK WIN")

    # Heatmap
    heatmap = generate_heatmap(results)
    output_path = args.output or str(input_path.parent / "heatmap.md")
    Path(output_path).write_text(heatmap, encoding="utf-8")
    print(f"\n✅ Heatmap générée : {output_path}")

    if args.json:
        json_path = str(input_path.parent / "scores.json")
        Path(json_path).write_text(json.dumps(results, indent=2, ensure_ascii=False))
        print(f"✅ JSON détaillé  : {json_path}")


if __name__ == "__main__":
    main()
