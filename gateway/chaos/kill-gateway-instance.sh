#!/usr/bin/env bash
echo "Starting Gateway Instance chaos test..."
echo "Generating a valid API key for chaos testing..."
REAL_KEY=$(curl -s -X POST -H "Content-Type: application/json" -H "X-Admin-Secret: super-secret-admin-token" -d '{"clientName":"Chaos","email":"chaos@test.com"}' http://localhost:8090/admin/keys | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
echo "Using API Key: $REAL_KEY"

echo "Ensuring instance on 8090 is running..."
curl -s -f http://localhost:8090/actuator/health >/dev/null || (echo "8090 not running. Start it first." && exit 1)

echo "Ensuring instance on 8091 is running..."
curl -s -f http://localhost:8091/actuator/health >/dev/null || (echo "8091 not running. Start it first." && exit 1)

echo "Firing 10 requests to 8091 to show it's alive..."
for i in {1..10}; do
  curl -s -o /dev/null -w "Req $i (8091 alive): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8091/test
done

echo "Killing instance on 8091..."
lsof -ti :8091 | xargs kill -9 2>/dev/null

echo "Wait 2 seconds..."
sleep 2

echo "Firing 10 requests to 8090 (should continue serving normally and rate limiting correctly)"
for i in {1..10}; do
  curl -s -o /dev/null -w "Req $i (8090 serving): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8090/test
done

echo "Chaos test complete. Remember to restart 8091 if needed."
