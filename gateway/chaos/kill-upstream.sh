#!/usr/bin/env bash
echo "Starting Upstream chaos test..."
echo "Generating a valid API key for chaos testing..."
REAL_KEY=$(curl -s -X POST -H "Content-Type: application/json" -H "X-Admin-Secret: super-secret-admin-token" -d '{"clientName":"Chaos","email":"chaos@test.com"}' http://localhost:8090/admin/keys | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
echo "Using API Key: $REAL_KEY"

echo "Stopping mock upstream task..."
# Since the mock upstream is a background node process, we'll just find and kill it.
pkill -f "node mock-upstream.js" || echo "Mock upstream not running"

echo "Wait 1 second..."
sleep 1

echo "Firing 10 requests to Gateway (should trip circuit breaker after 5 failures and return 503 instead of hanging)"
for i in {1..10}; do
  curl -s -o /dev/null -w "Req $i (breaker test): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8090/test
done

echo "Restarting mock upstream..."
node ../mock-upstream.js &
MOCK_PID=$!

echo "Wait 6 seconds (to allow circuit breaker cooldown to half-open)..."
sleep 6

echo "Firing 5 requests to Gateway (should recover to 200)"
for i in {1..5}; do
  curl -s -o /dev/null -w "Req $i (recovered): %{http_code}\n" -H "X-API-Key: $REAL_KEY" http://localhost:8090/test
done

echo "Killing mock upstream spawned by this script..."
kill $MOCK_PID

echo "Chaos test complete."
