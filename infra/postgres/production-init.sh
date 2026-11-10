#!/bin/sh
set -eu
psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    --set=app_password="$APP_DATABASE_PASSWORD" \
    --set=identity_password="$IDENTITY_DATABASE_PASSWORD" <<'SQL'
CREATE ROLE core LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
CREATE DATABASE core OWNER core;
CREATE ROLE identity LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'identity_password';
CREATE DATABASE keycloak OWNER identity;
SQL
