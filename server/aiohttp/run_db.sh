#!/bin/bash
# TODO: replace with docker-compose.yml
docker run --rm  -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=deep_noise --name postgres -p 5432:5432 -v ~/AGH/inzynierka/server/postgres_data:/var/lib/postgresql/data postgres
