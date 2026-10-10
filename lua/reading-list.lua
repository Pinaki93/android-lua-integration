local articles, loaded, exists = {}, false, false
local store, message, errors = nil, nil, {}
local screen, selected, status, tag = "list", nil, "All", ""
local draft = { url = "", title = "", tags = "", note = "" }
local requests, deletion = {}, false
local page, tag_page = 1, 1
local function trim(value) return reading.trim(value) end
local function text(value, style, tone) return ui.text { text = value, style = style, tone = tone } end
local function button(label, action, style) return ui.button { text = label, action = "reading." .. action, style = style } end
local function card(children, style, action)
  return ui.card { style = style, action = action and "reading." .. action, children = { ui.column { gap = 12, children = children } } }
end
local function filter(label, action, selected)
  return button(label .. (selected and " (selected)" or ""), action, selected and "selected" or "filter")
end
local function find(id, list)
  for index, article in ipairs(list or articles) do if article.id == id then return index, article end end
end
local function copy()
  local result = {}
  for index, article in ipairs(articles) do
    local item = {}
    for key, value in pairs(article) do item[key] = value end
    result[index] = item
  end
  return result
end
local function tags(value)
  local result, seen = {}, {}
  for part in string.gmatch(value .. ",", "(.-),") do
    local clean = trim(part)
    local key = reading.lower(clean)
    if clean ~= "" and not seen[key] then
      if reading.length(clean) > 30 then return nil end
      seen[key] = true
      result[#result + 1] = clean
    end
  end
  if #result > 5 then return nil end
  return result
end
local function array(value)
  if not reading.isArray(value) then return false end
  for key in pairs(value) do
    if type(key) ~= "number" or key % 1 ~= 0 or key < 1 or key > #value then return false end
  end
  return true
end
local function keys(value, allowed)
  if type(value) ~= "table" then return false end
  for key in pairs(value) do if not allowed[key] then return false end end
  for key in pairs(allowed) do if value[key] == nil then return false end end
  return true
end
local function valid(document)
  if not keys(document, { schemaVersion = true, articles = true }) or document.schemaVersion ~= 1 or not array(document.articles) then return false end
  local ids, urls = {}, {}
  for _, item in ipairs(document.articles) do
    if not keys(item, { id = true, url = true, title = true, titleSource = true, tags = true, note = true,
        isRead = true, createdAt = true, metadataState = true }) then return false end
    if type(item.id) ~= "string" or not string.match(item.id, "^[%w-]+$") or #item.id > 100 or ids[item.id] then return false end
    local ok, normalized = pcall(reading.normalize, item.url)
    if not ok or normalized ~= item.url or urls[item.url] then return false end
    if type(item.title) ~= "string" or trim(item.title) == "" or reading.length(item.title) > (item.titleSource == "fallback" and 2048 or 300) then return false end
    if item.titleSource ~= "user" and item.titleSource ~= "metadata" and item.titleSource ~= "fallback" then return false end
    if item.titleSource == "fallback" and item.title ~= item.url then return false end
    if type(item.note) ~= "string" or reading.length(item.note) > 4000 or type(item.isRead) ~= "boolean" then return false end
    if type(item.createdAt) ~= "number" or item.createdAt % 1 ~= 0 or item.createdAt < 0 or item.createdAt > 9007199254740991 then return false end
    if item.metadataState ~= "pending" and item.metadataState ~= "available" and item.metadataState ~= "failed" then return false end
    if not array(item.tags) or #item.tags > 5 then return false end
    local seen = {}
    for _, value in ipairs(item.tags) do
      if type(value) ~= "string" or value == "" or trim(value) ~= value or reading.length(value) > 30 or seen[reading.lower(value)] then return false end
      seen[reading.lower(value)] = true
    end
    ids[item.id], urls[item.url] = true, true
  end
  return true
end
local function load()
  local ok, document = pcall(function()
    store = storage.open("reading-list.json")
    return store.read()
  end)
  if not ok or document ~= nil and not valid(document) then
    loaded, message = false, "Could not load saved articles. Reload to recover; your document has not been reset."
    return
  end
  articles, exists, loaded, message = document and document.articles or {}, document ~= nil, true, nil
  requests = {}
end
local function save(candidate)
  local ok, failure = pcall(exists and store.update or store.create, { schemaVersion = 1, articles = candidate })
  if not ok then
    message = string.find(tostring(failure), "quota exceeded", 1, true) and "Storage is full. Shorten content or delete entries." or "Could not save articles. Please try again."
    return false
  end
  articles, exists, message = candidate, true, nil
  return true
end
local function details(article)
  selected, screen, deletion, errors = article.id, "detail", false, {}
  draft = { url = article.url, title = article.title, tags = table.concat(article.tags, ", "), note = article.note }
end
local function fetch(id)
  local _, article = find(id)
  if not article then return end
  local token = (requests[id] or 0) + 1
  requests[id] = token
  reading.fetch(article.url, function(result)
    local index, current = find(id)
    if not index or requests[id] ~= token then return end
    local candidate = copy()
    local updated = candidate[index]
    updated.metadataState = result.error and "failed" or "available"
    if not result.error and current.titleSource == "fallback" and current.title == article.url then
      updated.title, updated.titleSource = result.title, "metadata"
    end
    if save(candidate) then
      if selected == id and draft.title == current.title then draft.title = updated.title end
      if result.error == "redirect" then message = "Website redirected. Save its final HTTPS URL manually."
      elseif result.error then message = "Title unavailable. Your saved entry is usable offline; Retry when ready." end
    end
    requests[id] = nil
  end)
end
local function validate_form(editing)
  errors = {}
  local ok, url = pcall(reading.normalize, draft.url)
  if not ok then errors.url = "Enter an absolute HTTPS URL without credentials, on port 443 (maximum 2,048 characters)." end
  local title = trim(draft.title)
  local _, current = find(selected)
  if reading.length(title) > 300 and not (editing and current and current.titleSource == "fallback" and title == current.title) then errors.title = "Use at most 300 characters." end
  if reading.length(draft.note) > 4000 then errors.note = "Use at most 4,000 characters." end
  local normalized_tags = tags(draft.tags)
  if not normalized_tags then errors.tags = "Enter up to five comma-separated tags, at most 30 characters each." end
  if next(errors) then return nil end
  return { url = url, title = title, tags = normalized_tags, note = draft.note }
end
local function on_event(event)
  local action = string.sub(event.action, 9)
  if event.type == "text" then
    local field = string.match(action, "^field%.(%w+)$")
    if field then draft[field], errors[field] = event.value, nil end
    return
  end
  if action == "reload" then load(); return end
  if not loaded then return end
  if action == "add" then
    screen, selected, errors, message = "add", nil, {}, nil
    draft = { url = "", title = "", tags = "", note = "" }
  elseif action == "back" then screen, deletion, message = "list", false, nil
  elseif action == "save" then
    local form = validate_form(screen == "detail")
    if not form then return end
    if screen == "add" then
      for _, article in ipairs(articles) do
        if article.url == form.url then details(article); message = "Already saved"; return end
      end
      local article = { id = reading.id(), url = form.url, title = form.title ~= "" and form.title or form.url,
        titleSource = form.title ~= "" and "user" or "fallback", tags = form.tags, note = form.note,
        isRead = false, createdAt = reading.now(), metadataState = "pending" }
      local candidate = copy()
      candidate[#candidate + 1] = article
      if save(candidate) then details(article); fetch(article.id) end
    else
      local index, current = find(selected)
      if not index then return end
      local candidate = copy()
      local item = candidate[index]
      item.tags, item.note = form.tags, form.note
      if form.title ~= current.title then
        item.title, item.titleSource = form.title ~= "" and form.title or item.url, form.title ~= "" and "user" or "fallback"
      end
      if save(candidate) then details(item) end
    end
  elseif string.match(action, "^view%.") then
    local _, article = find(string.sub(action, 6))
    if article then details(article); message = nil end
  elseif string.match(action, "^status%.") then status, page = string.sub(action, 8), 1
  elseif action == "tag.all" then tag, page = "", 1
  elseif string.match(action, "^tag%.") then
    local list = {}
    for _, item in ipairs(articles) do for _, value in ipairs(item.tags) do list[#list + 1] = value end end
    tag, page = list[tonumber(string.sub(action, 5))] or "", 1
  elseif action == "nextTags" then tag_page = tag_page + 1
  elseif action == "previousTags" then tag_page = tag_page > 1 and tag_page - 1 or 1
  elseif action == "next" then page = page + 1
  elseif action == "previous" then page = page > 1 and page - 1 or 1
  elseif action == "cancelDelete" then deletion = false
  else
    local index, article = find(selected)
    if not article then return end
    if action == "toggle" then
      local candidate = copy(); candidate[index].isRead = not article.isRead; save(candidate)
    elseif action == "retry" then
      local candidate = copy(); candidate[index].metadataState = "pending"
      if save(candidate) then fetch(selected) end
    elseif action == "open" then
      local ok = pcall(reading.open, article.url)
      if not ok then message = "Could not open original. A browser and connection are needed." end
    elseif action == "delete" then deletion = true
    elseif action == "confirmDelete" and deletion then
      local candidate = copy(); table.remove(candidate, index)
      if save(candidate) then requests[selected] = nil; screen, selected, deletion = "list", nil, false end
    end
  end
end
local function render()
  local children = { text("YOUR PERSONAL LIBRARY", "label", "gold"), text("Reading list", "heading"), text("Saved list and notes available offline", "body", "secondary") }
  if message and loaded then children[#children + 1] = card({ text("Update", "title"), text(message) }, "lightOrange") end
  if not loaded then children[#children + 1] = card({ text("Your library needs attention", "title"), text(message), button("Reload saved articles", "reload") }, "outlined")
  elseif screen == "list" then
    local unread = 0
    for _, item in ipairs(articles) do if not item.isRead then unread = unread + 1 end end
    children[#children + 1] = text(#articles .. " saved · " .. unread .. " unread", "label", "secondary")
    children[#children + 1] = button("Add article", "add")
    local statuses = {}
    for _, value in ipairs({ "All", "Unread", "Read" }) do statuses[#statuses + 1] = filter(value, "status." .. value, status == value) end
    children[#children + 1] = ui.row { gap = 8, wrap = true, children = statuses }
    children[#children + 1] = text("Tag filter: " .. (tag == "" and "All tags" or tag))
    local tag_filters = { filter("All tags", "tag.all", tag == "") }
    local seen, position, tag_count = {}, 0, 0
    for _, item in ipairs(articles) do for _, value in ipairs(item.tags) do
      position = position + 1
      if not seen[reading.lower(value)] then
        tag_count = tag_count + 1
        if tag_count > (tag_page - 1) * 20 and tag_count <= tag_page * 20 then
          tag_filters[#tag_filters + 1] = filter("Tag: " .. value, "tag." .. position, reading.lower(tag) == reading.lower(value))
        end
        seen[reading.lower(value)] = true
      end
    end end
    children[#children + 1] = ui.row { gap = 8, wrap = true, children = tag_filters }
    if tag_page > 1 then children[#children + 1] = button("Previous tags", "previousTags", "quiet") end
    if tag_page * 20 < tag_count then children[#children + 1] = button("More tags", "nextTags", "quiet") end
    local visible = {}
    for _, item in ipairs(articles) do
      local matches = tag == ""
      for _, value in ipairs(item.tags) do if reading.lower(value) == reading.lower(tag) then matches = true end end
      if matches and (status == "All" or status == "Read" and item.isRead or status == "Unread" and not item.isRead) then visible[#visible + 1] = item end
    end
    table.sort(visible, function(a, b) return a.createdAt > b.createdAt or a.createdAt == b.createdAt and a.id > b.id end)
    if #visible == 0 then
      children[#children + 1] = card({ text(#articles == 0 and "A little space for good reading" or "Try another view", "title"),
        text(#articles == 0 and "No articles yet. Add one to your queue." or "No articles match these filters.", "body", "secondary"),
        #articles == 0 and button("Add your first article", "add") or ui.row { gap = 8, wrap = true, children = {
          button("All articles", "status.All", "quiet"), button("All tags", "tag.all", "quiet") } }
      }, #articles == 0 and "subtle" or "outlined")
    end
    -- shortcut: render 20 entries per page to stay within Lua UI tree limits; upgrade to windowed lists if needed.
    local last_page = (#visible - #visible % 20) / 20 + (#visible % 20 > 0 and 1 or 0)
    if last_page < 1 then last_page = 1 end
    if page > last_page then page = last_page end
    local last_index = page * 20 < #visible and page * 20 or #visible
    for index = (page - 1) * 20 + 1, last_index do
      local item = visible[index]
      local state = item.metadataState == "pending" and not requests[item.id] and "failed (interrupted; Retry)" or item.metadataState
      children[#children + 1] = ui.listItem { key = "reading." .. item.id, children = {
        card({ text(item.title, "title"), text(reading.hostname(item.url), "label", "secondary"),
          text(#item.tags > 0 and table.concat(item.tags, " · ") or "No tags", "label", "gold"),
          text((item.isRead and "Read" or "Unread") .. " · Title: " .. state, "label", "secondary")
        }, "default", "view." .. item.id)
      } }
    end
    if page > 1 then children[#children + 1] = button("Previous page", "previous", "quiet") end
    if page * 20 < #visible then children[#children + 1] = button("Next page", "next", "quiet") end
  else
    children[#children + 1] = button("Back to list / Cancel", "back", "quiet")
    local fields = {}
    for _, field in ipairs({ "url", "title", "tags", "note" }) do
      fields[#fields + 1] = ui.textField { label = ({ url = "HTTPS URL", title = "Title", tags = "Tags (comma-separated)", note = "Note" })[field],
        value = draft[field], action = "reading.field." .. field, error = errors[field], enabled = field ~= "url" or screen == "add", multiline = field == "note" }
    end
    children[#children + 1] = card(fields)
    local _, saved = find(selected)
    local changed = not saved or draft.title ~= saved.title or draft.tags ~= table.concat(saved.tags, ", ") or draft.note ~= saved.note
    children[#children + 1] = ui.button { text = "Save", action = "reading.save", enabled = changed }
    children[#children + 1] = text("Fetching a title contacts the article's website. Original articles require a connection.", "label", "secondary")
    if screen == "detail" then
      local _, item = find(selected)
      local interrupted = item.metadataState == "pending" and not requests[item.id]
      children[#children + 1] = text(item.isRead and "Read" or "Unread")
      children[#children + 1] = text("Title: " .. (interrupted and "failed (interrupted; Retry)" or item.metadataState))
      children[#children + 1] = card({ ui.row { gap = 8, wrap = true, children = {
        button(item.isRead and "Mark unread" or "Mark read", "toggle", "quiet"),
        button("Open original", "open", "quiet"), button("Retry title", "retry", "quiet") } } }, "subtle")
      children[#children + 1] = button("Delete article", "delete", "destructive")
      if deletion then children = { ui.dialog { title = "Delete article?", dismissAction = "reading.cancelDelete", children = {
        text(item.title), text(message or "This removes the saved entry and note."), button("Cancel", "cancelDelete", "quiet"), button("Delete", "confirmDelete", "destructive")
      } } } end
    end
  end
  return ui.column { gap = 16, children = children }
end
load()
return { render = render, onEvent = on_event }
