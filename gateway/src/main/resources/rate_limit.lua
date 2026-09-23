local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local request_id = ARGV[4]

local window_start = now - window_size

-- Remove all requests older than the sliding window
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- Count requests currently in the window
local current_count = redis.call('ZCARD', key)

local allowed = 0
if current_count < limit then
    -- Add the current request (score = now, member = request_id)
    redis.call('ZADD', key, now, request_id)
    allowed = 1
    current_count = current_count + 1
end

-- Set the key to expire so we don't leak memory
redis.call('EXPIRE', key, window_size)

-- Return 1/0 for allowed, and the current count
return { allowed, current_count }
