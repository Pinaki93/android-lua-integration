local records, api_page, ui_page = {}, 1, 1
local loading, complete, error_message = false, false, nil
local fetch

local function fail(message)
  loading, complete, error_message = false, false, message
end

fetch = function()
  if loading then return end
  loading, error_message = true, nil
  http.request({ url = "https://api.github.com/repos/lysine-dev/okhttp/contributors",
    query = { page = tostring(api_page), per_page = "100", anon = "1" },
    headers = { Accept = "application/vnd.github+json", ["User-Agent"] = "LuaPlayground" } }, function(response)
    if response.error then fail("Request failed: " .. response.error.code); return end
    if response.status == 204 then loading, complete = false, true; return end
    if response.status ~= 200 then
      if response.status == 403 or response.status == 429 then
        fail("GitHub rate limit. Remaining: " .. (response.headers["x-ratelimit-remaining"] or "unknown") ..
          ". Reset (Unix seconds): " .. (response.headers["x-ratelimit-reset"] or "unknown") ..
          ". Retry after: " .. (response.headers["retry-after"] or "unknown") .. " seconds.")
      else fail("GitHub returned HTTP " .. tostring(response.status)) end
      return
    end
    local data = response.json
    if not string.match(response.body, "^%s*%[") or type(data) ~= "table" or #data > 100 then fail("Invalid contributor data."); return end
    for key, _ in pairs(data) do
      if type(key) ~= "number" or key < 1 or key > #data or key % 1 ~= 0 then fail("Invalid contributor data."); return end
    end
    local page = {}
    for _, item in ipairs(data) do
      if type(item) ~= "table" or type(item.contributions) ~= "number" or
        item.contributions < 0 or item.contributions % 1 ~= 0 then fail("Invalid contributor data."); return end
      local anonymous = item.type == "Anonymous"
      local name = anonymous and item.name or item.login
      if type(name) ~= "string" or name == "" or #name > 200 then fail("Invalid contributor data."); return end
      local avatar = item.avatar_url
      if type(avatar) ~= "string" or #avatar > 2048 or
        not string.match(avatar, "^https://avatars%.githubusercontent%.com/[^%s#]*$") then avatar = nil end
      page[#page + 1] = { name = name, anonymous = anonymous, count = item.contributions, avatar = avatar }
    end
    for _, item in ipairs(page) do records[#records + 1] = item end
    loading = false
    if #data < 100 then complete = true else api_page = api_page + 1; fetch() end
  end)
end

local function on_event(event)
  if event.action == "contributors.refresh" and not loading then
    records, api_page, ui_page, complete = {}, 1, 1, false
    fetch()
  elseif event.action == "contributors.retry" and not loading then fetch()
  elseif event.action == "contributors.previous" and ui_page > 1 then ui_page = ui_page - 1
  elseif event.action == "contributors.next" and ui_page * 50 < #records then ui_page = ui_page + 1 end
end

local function render()
  local pages = (#records + 49 - (#records + 49) % 50) / 50
  if pages < 1 then pages = 1 end
  local children = {
    ui.text { text = "OkHttp Contributors", style = "title" },
    ui.text { text = "lysine-dev/okhttp" },
    ui.text { text = tostring(#records) .. (complete and " contributors" or " contributors fetched — incomplete") },
    ui.button { text = "Refresh", action = "contributors.refresh", enabled = not loading }
  }
  if loading then children[#children + 1] = ui.text { text = "Loading API page " .. tostring(api_page) .. "…" } end
  if error_message then
    children[#children + 1] = ui.text { text = error_message }
    children[#children + 1] = ui.button { text = "Retry", action = "contributors.retry", enabled = not loading }
  end
  if complete and #records == 0 then children[#children + 1] = ui.text { text = "No contributors found." } end
  children[#children + 1] = ui.text { text = "Page " .. tostring(ui_page) .. " of " .. tostring(pages) }
  children[#children + 1] = ui.row { children = {
    ui.button { text = "Previous", action = "contributors.previous", enabled = ui_page > 1 },
    ui.button { text = "Next", action = "contributors.next", enabled = ui_page * 50 < #records }
  } }
  local last = ui_page * 50
  if last > #records then last = #records end
  for index = (ui_page - 1) * 50 + 1, last do
    local item = records[index]
    local row = {
      ui.image { url = item.avatar, label = item.name .. " avatar", circleCrop = true }
    }
    row[#row + 1] = ui.column { children = {
      ui.text { text = item.name .. (item.anonymous and " (anonymous)" or "") },
      ui.text { text = tostring(item.count) .. " contributions" }
    } }
    children[#children + 1] = ui.listItem { key = "contributor." .. tostring(index), children = {
      ui.row { gap = 12, children = row }
    } }
  end
  return ui.column { gap = 12, children = children }
end

fetch()
return { render = render, onEvent = on_event }
