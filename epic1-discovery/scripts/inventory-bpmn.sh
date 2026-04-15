#!/bin/bash
# ============================================================
# inventory-bpmn.sh
# Inventorie les assets Camunda 7 dans un projet Java/Maven
# Usage: ./inventory-bpmn.sh /path/to/camunda-project
# Output: ../outputs/inventory.csv
# ============================================================

set -euo pipefail

PROJECT_DIR="${1:-.}"
OUTPUT_DIR="$(dirname "$0")/../outputs"
OUTPUT_CSV="$OUTPUT_DIR/inventory.csv"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

mkdir -p "$OUTPUT_DIR"

echo "=== Inventaire Camunda 7 → Kogito Migration ==="
echo "Projet scanné : $PROJECT_DIR"
echo "Output : $OUTPUT_CSV"
echo ""

# ─── En-tête CSV ────────────────────────────────────────────
echo "type,filename,path,details" > "$OUTPUT_CSV"

# ─── 1. Fichiers BPMN ────────────────────────────────────────
echo "📋 Scanning BPMN files..."
find "$PROJECT_DIR" -name "*.bpmn" -o -name "*.bpmn2" | while read -r f; do
    # Extraire le process ID depuis le XML
    process_id=$(grep -oP 'id="[^"]+"' "$f" | head -1 | grep -oP '"[^"]+"' | tr -d '"' || echo "unknown")
    echo "BPMN,$(basename "$f"),$f,$process_id" >> "$OUTPUT_CSV"
    echo "  ✓ BPMN: $(basename "$f") [id=$process_id]"
done

# ─── 2. Fichiers DMN ─────────────────────────────────────────
echo "📋 Scanning DMN files..."
find "$PROJECT_DIR" -name "*.dmn" | while read -r f; do
    decision_id=$(grep -oP 'id="[^"]+"' "$f" | head -1 | grep -oP '"[^"]+"' | tr -d '"' || echo "unknown")
    echo "DMN,$(basename "$f"),$f,$decision_id" >> "$OUTPUT_CSV"
    echo "  ✓ DMN: $(basename "$f") [id=$decision_id]"
done

# ─── 3. Java Delegates ───────────────────────────────────────
echo "📋 Scanning Java Delegates..."
find "$PROJECT_DIR" -name "*.java" | xargs grep -l "JavaDelegate\|DelegateExecution\|implements JavaDelegate" 2>/dev/null | while read -r f; do
    class_name=$(basename "$f" .java)
    echo "DELEGATE,${class_name}.java,$f,implements JavaDelegate" >> "$OUTPUT_CSV"
    echo "  ✓ Delegate: $class_name"
done

# ─── 4. References delegateExpression dans BPMN ─────────────
echo "📋 Scanning delegateExpression references..."
find "$PROJECT_DIR" -name "*.bpmn" | xargs grep -ho 'delegateExpression="[^"]*"' 2>/dev/null | sort -u | while read -r expr; do
    echo "DELEGATE_EXPR,,,$expr" >> "$OUTPUT_CSV"
    echo "  ✓ DelegateExpr: $expr"
done

# ─── 5. Scripts JSR-223 ──────────────────────────────────────
echo "📋 Scanning JSR-223 scripts in BPMN..."
find "$PROJECT_DIR" -name "*.bpmn" | xargs grep -l "scriptFormat" 2>/dev/null | while read -r f; do
    langs=$(grep -oP 'scriptFormat="[^"]+"' "$f" | sort -u | tr '\n' ',' || echo "")
    echo "SCRIPT,$(basename "$f"),$f,$langs" >> "$OUTPUT_CSV"
    echo "  ✓ Script in BPMN: $(basename "$f") [$langs]"
done

# ─── 6. External Tasks / Topics ──────────────────────────────
echo "📋 Scanning External Tasks..."
find "$PROJECT_DIR" -name "*.bpmn" | xargs grep -ho 'topic="[^"]*"' 2>/dev/null | sort -u | while read -r topic; do
    echo "EXTERNAL_TASK,,,$topic" >> "$OUTPUT_CSV"
    echo "  ✓ External Task topic: $topic"
done

# ─── 7. Connectors Camunda Connect ───────────────────────────
echo "📋 Scanning Camunda Connectors..."
find "$PROJECT_DIR" -name "*.bpmn" | xargs grep -l "camunda:connector" 2>/dev/null | while read -r f; do
    connectors=$(grep -oP 'connectorId>[^<]+' "$f" | sed 's/connectorId>//' | sort -u | tr '\n' ',' || echo "")
    echo "CONNECTOR,$(basename "$f"),$f,$connectors" >> "$OUTPUT_CSV"
    echo "  ✓ Connector in: $(basename "$f") [$connectors]"
done

# ─── 8. User Forms ───────────────────────────────────────────
echo "📋 Scanning User Forms..."
find "$PROJECT_DIR" -name "*.form" -o -name "*.html" | grep -i form | while read -r f; do
    echo "FORM,$(basename "$f"),$f,user-form" >> "$OUTPUT_CSV"
    echo "  ✓ Form: $(basename "$f")"
done

# ─── Résumé ──────────────────────────────────────────────────
echo ""
echo "=== RÉSUMÉ ==="
echo "BPMN    : $(grep -c "^BPMN," "$OUTPUT_CSV" || echo 0)"
echo "DMN     : $(grep -c "^DMN," "$OUTPUT_CSV" || echo 0)"
echo "Delegates: $(grep -c "^DELEGATE," "$OUTPUT_CSV" || echo 0)"
echo "Scripts : $(grep -c "^SCRIPT," "$OUTPUT_CSV" || echo 0)"
echo "Ext Tasks: $(grep -c "^EXTERNAL_TASK," "$OUTPUT_CSV" || echo 0)"
echo "Connectors: $(grep -c "^CONNECTOR," "$OUTPUT_CSV" || echo 0)"
echo "Forms   : $(grep -c "^FORM," "$OUTPUT_CSV" || echo 0)"
echo ""
echo "✅ Inventaire complet : $OUTPUT_CSV"
echo "→ Lancer le scoring : python3 complexity-scorer.py --input $OUTPUT_CSV"
