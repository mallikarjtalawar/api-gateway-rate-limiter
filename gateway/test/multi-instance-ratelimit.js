const http = require('http');

async function runTest() {
    console.log('Starting Multi-Instance Rate Limit Test...');
    
    // 1. Generate an API Key
    const keyReq = await fetch('http://localhost:8090/admin/keys', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Admin-Secret': 'super-secret-admin-token' },
        body: JSON.stringify({ clientName: 'LoadTester', email: 'test@example.com' })
    });
    
    if (!keyReq.ok) {
        console.error('Failed to generate API key', await keyReq.text());
        return;
    }
    const keyData = await keyReq.json();
    const apiKey = keyData.apiKey;
    console.log(`Generated API Key: ${apiKey.substring(0, 5)}...`);

    const TOTAL_REQUESTS = 20;
    const EXPECTED_SUCCESS = 10;
    const EXPECTED_429 = 10;
    
    console.log(`Firing ${TOTAL_REQUESTS} concurrent requests (split across 8090 and 8091)...`);
    
    const promises = [];
    for (let i = 0; i < TOTAL_REQUESTS; i++) {
        const port = (i % 2 === 0) ? 8090 : 8091;
        promises.push(
            fetch(`http://localhost:${port}/test`, {
                headers: { 'X-API-Key': apiKey }
            }).then(res => res.status).catch(e => -1)
        );
    }
    
    const results = await Promise.all(promises);
    
    let successCount = 0;
    let rateLimitedCount = 0;
    let otherCount = 0;
    
    results.forEach(status => {
        if (status === 200) successCount++;
        else if (status === 429) rateLimitedCount++;
        else otherCount++;
    });
    
    console.log('\n--- Test Results ---');
    console.log(`Total Requests: ${TOTAL_REQUESTS}`);
    console.log(`200 OK (Allowed):  ${successCount} (Expected: ${EXPECTED_SUCCESS})`);
    console.log(`429 Too Many:      ${rateLimitedCount} (Expected: ${EXPECTED_429})`);
    if (otherCount > 0) console.log(`Other (Errors):    ${otherCount}`);
    
    if (successCount === EXPECTED_SUCCESS && rateLimitedCount === EXPECTED_429) {
        console.log('\n✅ TEST PASSED: Distributed rate limiting works correctly across multiple instances!');
    } else {
        console.log('\n❌ TEST FAILED: Counts do not match expected values.');
    }
}

runTest();
