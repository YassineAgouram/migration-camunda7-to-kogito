#!/bin/bash
# ============================================================
# bootstrap.sh — Génère un service Kogito depuis le template
# Usage: ./bootstrap.sh --name mon-service --package com.example
# ============================================================

set -euo pipefail

# ─── Valeurs par défaut ──────────────────────────────────────
SERVICE_NAME=""
PACKAGE="com.example.kogito"
OUTPUT_DIR=""
TEMPLATE_DIR="$(dirname "$0")/../../templates/process-service"

# ─── Parsing des arguments ───────────────────────────────────
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --name) SERVICE_NAME="$2"; shift ;;
        --package) PACKAGE="$2"; shift ;;
        --output) OUTPUT_DIR="$2"; shift ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# ─── Validation ──────────────────────────────────────────────
if [[ -z "$SERVICE_NAME" ]]; then
    echo "❌ --name est requis"
    echo "Usage: $0 --name mon-processus --package com.example"
    exit 1
fi

OUTPUT_DIR="${OUTPUT_DIR:-$(pwd)/$SERVICE_NAME}"

echo "=== Kogito Service Bootstrap ==="
echo "Service name : $SERVICE_NAME"
echo "Package      : $PACKAGE"
echo "Output dir   : $OUTPUT_DIR"
echo ""

# ─── Copie du template ───────────────────────────────────────
if [[ -d "$OUTPUT_DIR" ]]; then
    echo "❌ Le répertoire $OUTPUT_DIR existe déjà"
    exit 1
fi

cp -r "$TEMPLATE_DIR" "$OUTPUT_DIR"
echo "✓ Template copié"

# ─── Substitutions ───────────────────────────────────────────
ARTIFACT_ID=$(echo "$SERVICE_NAME" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr '_' '-')
CLASS_PREFIX=$(echo "$SERVICE_NAME" | sed 's/-\([a-z]\)/\U\1/g;s/^\([a-z]\)/\U\1/')

# pom.xml
sed -i "s/kogito-process-service-template/$ARTIFACT_ID/g" "$OUTPUT_DIR/pom.xml"
sed -i "s/com\.example\.kogito\b/$PACKAGE/g" "$OUTPUT_DIR/pom.xml"
echo "✓ pom.xml mis à jour"

# application.properties
sed -i "s/kogito-process-service/$ARTIFACT_ID/g" \
    "$OUTPUT_DIR/src/main/resources/application.properties"
echo "✓ application.properties mis à jour"

# Fichiers Java
find "$OUTPUT_DIR/src" -name "*.java" | while read -r f; do
    sed -i "s/com\.example\.kogito/$PACKAGE/g" "$f"
done
echo "✓ Packages Java mis à jour"

# Renommer les répertoires du package
OLD_PACKAGE_DIR="$OUTPUT_DIR/src/main/java/com/example/kogito"
NEW_PACKAGE_DIR="$OUTPUT_DIR/src/main/java/$(echo "$PACKAGE" | tr '.' '/')"

if [[ "$OLD_PACKAGE_DIR" != "$NEW_PACKAGE_DIR" ]]; then
    mkdir -p "$(dirname "$NEW_PACKAGE_DIR")"
    mv "$OLD_PACKAGE_DIR" "$NEW_PACKAGE_DIR" 2>/dev/null || true
    echo "✓ Répertoires package renommés"
fi

# ─── Résumé ──────────────────────────────────────────────────
echo ""
echo "✅ Service '$SERVICE_NAME' généré avec succès !"
echo ""
echo "Prochaines étapes :"
echo "  1. cd $OUTPUT_DIR"
echo "  2. Adapter src/main/resources/*.bpmn avec votre processus"
echo "  3. Adapter les Work Item Handlers dans src/main/java/"
echo "  4. ./mvnw quarkus:dev"
echo ""
echo "Documentation : ../epic3-factory/README.md"
