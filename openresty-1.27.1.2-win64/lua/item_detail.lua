local cache = ngx.shared.item_cache

-- 1. 解析商品ID
local id = ngx.var.uri:match("/api/items/(%d+)")
if not id then
    ngx.status = 400
    ngx.say('{"msg":"Invalid item id"}')
    return
end

local cache_key = "item:" .. id

-- 2. 查本地缓存
local val = cache:get(cache_key)
if val then
    ngx.header["Content-Type"] = "application/json"
    ngx.header["X-Cache-Status"] = "HIT"
    ngx.say(val)
    return
end

-- 3. 缓存未命中，请求后端
local http = require "resty.http"
local httpc = http.new()
local res, err = httpc:request_uri("http://localhost:8080/items/" .. id, {
    method = "GET",
    keepalive = false
})

if not res or res.status ~= 200 then
    ngx.status = 502
    ngx.say('{"msg":"backend error"}')
    return
end

-- 4. 写入本地缓存，设置10分钟过期
cache:set(cache_key, res.body, 600)

ngx.header["Content-Type"] = "application/json"
ngx.header["X-Cache-Status"] = "MISS"
ngx.say(res.body)