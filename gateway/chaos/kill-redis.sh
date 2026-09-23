#!/usr/bin/env bash
echo "Starting Redis chaos test..."
echo "Generating a valid API key for chaos testing..."
REAL_KEY=$(curl -s -X POST -H "Content-Type: application/json" -H "X-Admin-Secret: super-secret-admin-token" -d '{"clientName":"Chaos","email":"chaos@test.com"}' http://localhost:8090/admin/keys | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
echo "Using API Key: $REAL_KEY"
echo "Stopping Redis via brew..."
brew services stop redis

echo "Wait 2 seconds..."
sleep 2

echo "Firing 5 requests to Gateway (should fail OPEN with 200, since Redis is down but within 5s grace period)"
for i in {1..5}; do
  curl -s -o /dev/null -w "Req $i (grace period): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8090/test
done

echo "Wait 6 seconds (to exceed 5s grace period)..."
sleep 6

echo "Firing 5 requests to Gateway (should fail CLOSED with 503)"
for i in {1..5}; do
  curl -s -o /dev/null -w "Req $i (fail closed): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8090/test
done

echo "Starting Redis back up..."
brew services start redis

echo "Wait 3 seconds for Redis to start..."
sleep 3

echo "Firing 5 requests to Gateway (should recover to 200/429)"
for i in {1..5}; do
  curl -s -o /dev/null -w "Req $i (recovered): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8090/test
done

echo "Chaos test complete."
