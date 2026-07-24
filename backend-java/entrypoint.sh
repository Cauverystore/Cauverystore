#!/bin/sh
echo "=== Entrypoint ==="
echo "Original SPRING_DATASOURCE_URL: [$SPRING_DATASOURCE_URL]"
if echo "$SPRING_DATASOURCE_URL" | grep -q "^postgres://"; then
  JDBC_URL=$(echo "$SPRING_DATASOURCE_URL" | sed 's|^postgres://|jdbc:postgresql://|')
  echo "Converted to: [$JDBC_URL]"
  exec env SPRING_DATASOURCE_URL="$JDBC_URL" java -jar app.jar
fi
echo "No conversion needed, starting directly"
exec java -jar app.jar
