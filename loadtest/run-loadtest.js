const http = require('http');

const GATEWAY_URL = 'http://localhost:8090';
const ADMIN_SECRET = 'super-secret-admin-token';
const CONCURRENT_REQUESTS = 200;

async function runTest() {
    console.log('--- Phase 8: Chaos Engineering & Load Testing ---');
    console.log('1. Calling Admin API to generate a new API Key...');
    
    const apiKey = await generateApiKey();
    console.log(`[SUCCESS] Generated API Key: ${apiKey}`);

    console.log(`\n2. Blasting ${CONCURRENT_REQUESTS} concurrent requests through the gateway...`);
    const results = await blastGateway(apiKey, CONCURRENT_REQUESTS);
    
    console.log('\n--- Load Test Results ---');
    console.log(`Total Requests Sent: ${CONCURRENT_REQUESTS}`);
    console.log(`Success (200 OK): ${results.status200}`);
    console.log(`Blocked by Rate Limiter (429 Too Many Requests): ${results.status429}`);
    console.log(`Other Status Codes: ${results.other}`);
    
    if (results.status200 === 10 && results.status429 === 190) {
        console.log('[PASS] Exactly 10 requests allowed, and exactly 190 blocked!');
    } else {
        console.log('[FAIL] The numbers do not perfectly match the rate limit rules!');
    }

    // Wait a brief moment for asynchronous UsageAggregatorService (Redis) to finish updating
    await new Promise(resolve => setTimeout(resolve, 500));

    console.log(`\n3. Calling Admin API to verify Redis Usage Counters...`);
    const usage = await getUsageMetrics(apiKey);
    console.log('Redis Counters:', usage);
    
    if (parseInt(usage.total) === CONCURRENT_REQUESTS && 
        parseInt(usage.status_200) === results.status200 && 
        parseInt(usage.status_429) === results.status429) {
        console.log('[PASS] Redis asynchronous counters perfectly match the real traffic!');
    } else {
        console.log('[FAIL] Redis counters do not match!');
    }
}

function generateApiKey() {
    return new Promise((resolve, reject) => {
        const req = http.request(`${GATEWAY_URL}/admin/keys`, {
            method: 'POST',
            headers: { 'X-Admin-Secret': ADMIN_SECRET }
        }, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode === 200) {
                    resolve(JSON.parse(data).apiKey);
                } else {
                    reject(`Admin API failed with status ${res.statusCode}`);
                }
            });
        });
        req.on('error', reject);
        req.end();
    });
}

function blastGateway(apiKey, count) {
    return new Promise((resolve) => {
        let completed = 0;
        let status200 = 0;
        let status429 = 0;
        let other = 0;

        const makeRequest = () => {
            const req = http.request(`${GATEWAY_URL}/test`, {
                method: 'GET',
                headers: { 'X-API-Key': apiKey }
            }, res => {
                res.on('data', () => {}); // consume response
                res.on('end', () => {
                    if (res.statusCode === 200) status200++;
                    else if (res.statusCode === 429) status429++;
                    else other++;
                    
                    completed++;
                    if (completed === count) resolve({ status200, status429, other });
                });
            });
            req.on('error', () => {
                other++;
                completed++;
                if (completed === count) resolve({ status200, status429, other });
            });
            req.end();
        };

        // Fire all requests immediately
        for (let i = 0; i < count; i++) {
            makeRequest();
        }
    });
}

function getUsageMetrics(apiKey) {
    return new Promise((resolve, reject) => {
        const req = http.request(`${GATEWAY_URL}/admin/usage/${apiKey}`, {
            method: 'GET',
            headers: { 'X-Admin-Secret': ADMIN_SECRET }
        }, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode === 200) {
                    resolve(JSON.parse(data));
                } else {
                    reject(`Admin API failed with status ${res.statusCode}`);
                }
            });
        });
        req.on('error', reject);
        req.end();
    });
}

runTest().catch(console.error);
