local cache = ngx.shared.item_cache
local cjson = require "cjson.safe"

-- 1. 获取ids参数
local args = ngx.req.get_uri_args()
local ids_param = args.ids
if not ids_param then
    ngx.status = 400
    ngx.say('{"msg":"Missing ids parameter"}')
    return
end

-- 只支持ids=1,2,3 这种格式
local ids = {}
for id in string.gmatch(ids_param, "%d+") do
    table.insert(ids, id)
end

local result = {}
local miss_ids = {}

-- 2. 先查本地缓存
for _, id in ipairs(ids) do
    local cache_key = "item:" .. id
    local val = cache:get(cache_key)
    if val then
        -- 这里假设val是json字符串，解包后插入
        local obj = cjson.decode(val)
        if obj then
            table.insert(result, obj)
        end
    else
        table.insert(miss_ids, id)
    end
end

-- 3. 对未命中的id去后端查
if #miss_ids > 0 then
    local http = require "resty.http"
    local httpc = http.new()
    local miss_ids_str = table.concat(miss_ids, ",")
    local res, err = httpc:request_uri("http://localhost:8080/items?ids=" .. miss_ids_str, {
        method = "GET",
        keepalive = false
    })
    if res and res.status == 200 then
        local items = cjson.decode(res.body)
        if type(items) == "table" then
            for _, item in ipairs(items) do
                -- 缓存每个item
                cache:set("item:" .. tostring(item.id), cjson.encode(item), 600)
                table.insert(result, item)
            end
        end
    end
end

ngx.header["Content-Type"] = "application/json"
ngx.say(cjson.encode(result))