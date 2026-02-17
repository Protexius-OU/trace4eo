#!/bin/bash
set -e

echo "Building backend image..."
./gradlew :tracing-system:bootBuildImage

echo "Building frontend..."
cd tracing-ui
npm install
npm run build
cd ..

echo "Building Docker images..."
docker compose build

echo "Done! Run './start-dev.sh' to start."
