#!/bin/sh
envsubst '$KEYCLOAK_URL' \
  < /usr/share/nginx/html/env-config.js.template \
  > /usr/share/nginx/html/env-config.js
