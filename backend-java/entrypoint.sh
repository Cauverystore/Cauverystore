#!/bin/sh
if [ -n "$DATABASE_URL" ]; then
  # Convert postgres://user:pass@host:port/db -> jdbc:postgresql://host:port/db
  JDBC_URL=$(echo "$DATABASE_URL" | sed 's|^postgres://|jdbc:postgresql://|')
  export SPRING_DATASOURCE_URL="$JDBC_URL"
fi
exec java -jar app.jar
