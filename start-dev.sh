#!/bin/bash
set -e

docker compose up -d --wait
echo "All services started. Frontend at http://localhost:3000, backend at http://localhost:8080"
