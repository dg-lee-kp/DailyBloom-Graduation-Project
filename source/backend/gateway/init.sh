#!/bin/sh

DBNAME="${POSTGRES_DB}"
HOSTNAME="${POSTGRES_HOST}"
USERNAME="${POSTGRES_USER}"
PORT="${POSTGRES_PORT}"

until pg_isready -d "$DBNAME" -h "$HOSTNAME" -U "$USERNAME" -p "$PORT"
do
	sleep 1
done

echo "PostgreSQL is ready."
exec "$@"
