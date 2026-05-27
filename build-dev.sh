#!/bin/bash
set -e

echo "Building all modules..."
./gradlew clean build
cd tracing-ui
npm install
npm run build
cd ..

echo "Building backend Docker image..."
./gradlew :tracing-system:bootBuildImage

echo "Building frontend Docker image..."
docker compose build frontend

echo "Done! Run './start-dev.sh' to start."
