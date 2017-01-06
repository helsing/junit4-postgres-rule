#!/usr/bin/env bash

# Start database
docker run --name database -d postgres

# Connect to database
psql -h localhost -p 58593 -d postgres -U postgres
psql -h localhost -p 58882 -d postgres -U postgres

# Execute command
docker exec database psql -U postgres -c "CREATE DATABASE org.example"
docker exec mad_mayer psql -U postgres -c "CREATE DATABASE org.example"
docker exec postgres-rule-52441 psql -t -U postgres -d postgres -c "CREATE DATABASE nnn"

# Connect via psql
docker run -it --rm --link database:postgres postgres psql -h postgres -U postgres
