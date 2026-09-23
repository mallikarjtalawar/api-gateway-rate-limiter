import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        direct_to_upstream: {
            executor: 'constant-arrival-rate',
            rate: 200, // 200 requests per second
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 50,
            maxVUs: 100,
            exec: 'direct',
        },
        via_gateway: {
            executor: 'constant-arrival-rate',
            rate: 200,
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 50,
            maxVUs: 100,
            startTime: '15s', // Start after the first scenario finishes + 5s cooldown
            exec: 'gateway',
        },
    },
    thresholds: {
        'http_req_duration{scenario:direct_to_upstream}': ['p(99)<200'],
        'http_req_duration{scenario:via_gateway}': ['p(99)<300'], // Gateway adds some overhead
    },
};

export function direct() {
    const res = http.get('http://localhost:8081/test?param=1');
    check(res, {
        'status is 200': (r) => r.status === 200,
        'has mock header': (r) => r.headers['X-Mock-Upstream'] === 'true',
    });
}

export function gateway() {
    const res = http.get('http://localhost:8090/test?param=1');
    check(res, {
        'status is 200': (r) => r.status === 200,
        'has mock header': (r) => r.headers['X-Mock-Upstream'] === 'true',
    });
}
