#!/bin/bash

# Test script to verify ClassLoader fix
# This script tests the Math_1b project to ensure ClassLoader issues are resolved

set -e

echo "=========================================="
echo "ClassLoader Fix Verification Test"
echo "=========================================="
echo ""

# Check if Defects4J is available
if ! command -v defects4j &> /dev/null; then
    echo "ERROR: defects4j command not found!"
    echo "Please install Defects4J first."
    exit 1
fi

# Set up test project
PROJECT_DIR="/tmp/defects4j_projects/Math_1b"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Setting up Math_1b project..."
    mkdir -p /tmp/defects4j_projects
    cd /tmp/defects4j_projects
    defects4j checkout -p Math -v 1b -w Math_1b
    cd Math_1b
    defects4j compile
    defects4j test
else
    echo "Using existing Math_1b project at $PROJECT_DIR"
fi

# Compile ARJA
echo ""
echo "Compiling ARJA..."
cd /home/x/arja
mvn clean compile -DskipTests -q

if [ $? -ne 0 ]; then
    echo "ERROR: ARJA compilation failed!"
    exit 1
fi

echo "✅ ARJA compiled successfully"
echo ""

# Run ARJA with minimal parameters to test ClassLoader
echo "Running ARJA to test ClassLoader fix..."
echo "This will run for 30 seconds to verify:"
echo "  1. No ClassNotFoundException for Number/String"
echo "  2. Faulty lines are found (> 0)"
echo "  3. Modification points are generated (> 0)"
echo "  4. Fitness evaluation starts"
echo ""

timeout 30s java -cp "target/classes:lib/*:external/lib/*" \
    us.msu.cse.repair.Main Arja \
    -DsrcJavaDir $PROJECT_DIR/src/main/java \
    -DbinJavaDir $PROJECT_DIR/target/classes \
    -DbinTestDir $PROJECT_DIR/target/test-classes \
    -Ddependences $PROJECT_DIR/lib/commons-discovery-0.5.jar \
    -DexternalProjRoot $PROJECT_DIR \
    -DmaxGenerations 1 \
    -DpopulationSize 5 \
    2>&1 | tee /tmp/arja_classloader_test.log

echo ""
echo "=========================================="
echo "Test Results:"
echo "=========================================="
grep -E "(Faulty lines|Modification points|ClassNotFoundException|Starting fitness)" /tmp/arja_classloader_test.log || true
echo ""
echo "Full log saved to: /tmp/arja_classloader_test.log"