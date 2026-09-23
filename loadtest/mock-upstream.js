const http = require('http');

const port = process.env.PORT || 8081;
const delayMs = parseInt(process.env.DELAY_MS || '10', 10);

const server = http.createServer((req, res) => {
    let body = [];
    req.on('data', chunk => {
        body.push(chunk);
    });
    
    req.on('end', () => {
        const responseData = JSON.stringify({
            message: "Hello from mock upstream",
            path: req.url,
            method: req.method
        });
        
        setTimeout(() => {
            res.writeHead(200, {
                'Content-Type': 'application/json',
                'X-Mock-Upstream': 'true'
            });
            res.end(responseData);
        }, delayMs);
    });
});

server.listen(port, () => {
    console.log(`Mock upstream server listening on port ${port} with delay ${delayMs}ms`);
});
