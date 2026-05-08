local key            = KEYS[1]
local capacity       = tonumber(ARGV[1])
local refill_tokens  = tonumber(ARGV[2])
local refill_seconds = tonumber(ARGV[3])
local now_ms         = tonumber(ARGV[4])

-- Read current state from Redis hash
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local tokens = tonumber(bucket[1])
local last_refill_ms = tonumber(bucket[2])

-- First request: initialise bucket to full capacity
if tokens == nil or last_refill_ms == nil then
    tokens = capacity
    last_refill_ms = now_ms
end

-- Calculate how many tokens to refill based on elapsed time
local elapsed_ms = now_ms - last_refill_ms
local refill_interval_ms = refill_seconds * 1000
local refill_amount  = math.floor(elapsed_ms / refill_interval_ms) * refill_tokens

if refill_amount > 0 then
    tokens = math.min(capacity, tokens + refill_amount)
    -- Advance last_refill_ms by the exact number of full intervals consumed
    last_refill_ms = last_refill_ms + math.floor(elapsed_ms / refill_interval_ms) * refill_interval_ms
end

-- Try to consume 1 token
local allowed   = 0
local remaining = 0

if tokens >= 1 then
    tokens    = tokens - 1
    allowed   = 1
    remaining = tokens
end

-- Persist updated bucket (TTL = capacity * refill_seconds + buffer, min 3600s)
local ttl = math.max(3600, capacity * refill_seconds + 60)
redis.call('HMSET', key, 'tokens', tokens, 'last_refill_ms', last_refill_ms)
redis.call('EXPIRE', key, ttl)

return { allowed, remaining }
