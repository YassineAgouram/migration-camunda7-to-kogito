# EPIC 1 — Discovery & Assessment

## Objectif

Inventorier l'existant Camunda 7, qualifier les écarts et préparer les lots de migration.

---

## Story 1.1 — Inventorier les assets Camunda 7

### Script d'inventaire BPMN/DMN

Voir `scripts/inventory-bpmn.sh`

Ce script scanne un projet Maven/Gradle et produit un CSV listant :
- Tous les fichiers `.bpmn` et `.dmn`
- Les Java Delegates référencés
- Les scripts JSR-223
- Les external tasks (topics)
- Les connectors Camunda Connect

### Checklist d'inventaire manuel

- [ ] BPMN et versions identifiés
- [ ] DMN et points d'appel documentés
- [ ] User forms Camunda / forms externes recensés
- [ ] Java Delegates, listeners, delegateExpressions listés
- [ ] Scripts JSR-223 (Groovy, JavaScript) listés
- [ ] Connectors Camunda Connect listés
- [ ] External tasks et topics listés
- [ ] Variables, types et sérialisation documentés
- [ ] History level, cleanup et custom handlers documentés
- [ ] Plugins moteur, identity integrations, job executor settings documentés

---

## Story 1.2 — Évaluer la complexité de migration

### Script de scoring

Voir `scripts/complexity-scorer.py`

Prend en entrée le CSV d'inventaire et produit :
- Score par processus
- Classification par lot
- Identification des blockers et quick wins

### Grille de scoring

Voir `../docs/complexity-scoring-grid.md`

---

## Story 1.3 — Stratégie de coexistence

Voir `../docs/coexistence-strategy.md`

### Checklist Story 1.3

- [ ] Mode coexistence défini et validé
- [ ] Règles de routage documentées
- [ ] Plan de rollback écrit et testé
- [ ] Stratégie de fin de vie Camunda validée
- [ ] Critères de bascule complète définis
