local MAX_ITEMS = 100
local MAX_TITLE = 200
local MAX_ID = 2147483647

local items = {}
local next_id = 1
local draft = ""
local draft_error = nil
local storage_error = nil
local loaded = false
local exists = false

local function exact_keys(value, allowed)
  if type(value) ~= "table" then return false end
  for key, _ in pairs(value) do if not allowed[key] then return false end end
  for key, _ in pairs(allowed) do if value[key] == nil then return false end end
  return true
end

local function code_points(value)
  if type(value) ~= "string" then return nil end
  local count, index = 0, 1
  while index <= #value do
    local first = string.byte(value, index)
    local width = first < 128 and 1 or (first < 224 and 2 or (first < 240 and 3 or 4))
    if width == 3 and index + 5 <= #value then
      local second, third = string.byte(value, index + 1), string.byte(value, index + 2)
      local point = (first - 224) * 4096 + (second - 128) * 64 + third - 128
      if point >= 55296 and point <= 56319 then
        local next_first, next_second, next_third = string.byte(value, index + 3), string.byte(value, index + 4), string.byte(value, index + 5)
        local next_point = (next_first - 224) * 4096 + (next_second - 128) * 64 + next_third - 128
        if next_point >= 56320 and next_point <= 57343 then width = 6 end
      end
    end
    count, index = count + 1, index + width
  end
  return count
end

local function valid_title(title)
  local length = type(title) == "string" and code_points(title) or nil
  return length ~= nil and length > 0 and length <= MAX_TITLE and title == string.match(title, "^%s*(.-)%s*$")
end

local function validate(document)
  if not exact_keys(document, { version = true, nextId = true, items = true }) then return nil end
  if document.version ~= 1 or type(document.nextId) ~= "number" or document.nextId % 1 ~= 0 or
      document.nextId < 1 or document.nextId > MAX_ID or type(document.items) ~= "table" or
      #document.items > MAX_ITEMS then return nil end
  local seen, maximum = {}, 0
  for key, _ in pairs(document.items) do
    if type(key) ~= "number" or key % 1 ~= 0 or key < 1 or key > #document.items then return nil end
  end
  for index = 1, #document.items do
    local item = document.items[index]
    if not exact_keys(item, { id = true, title = true, completed = true }) or
        type(item.id) ~= "number" or item.id % 1 ~= 0 or item.id < 1 or item.id >= MAX_ID or
        seen[item.id] or not valid_title(item.title) or type(item.completed) ~= "boolean" then return nil end
    seen[item.id] = true
    if item.id > maximum then maximum = item.id end
  end
  if document.nextId <= maximum then return nil end
  return document
end

local function load()
  local ok, value = pcall(store.read)
  if not ok then
    loaded = false
    storage_error = "Could not load saved tasks. Reload before editing."
    return
  end
  if value == nil then
    items, next_id, exists = {}, 1, false
  else
    local document = validate(value)
    if not document then
      loaded = false
      storage_error = "Could not load saved tasks. Reload before editing."
      return
    end
    items, next_id, exists = document.items, document.nextId, true
  end
  loaded, storage_error = true, nil
end

local function copy_items()
  local copy = {}
  for index, item in ipairs(items) do
    copy[index] = { id = item.id, title = item.title, completed = item.completed }
  end
  return copy
end

local function save(candidate, candidate_next_id)
  local document = { version = 1, nextId = candidate_next_id, items = candidate }
  local ok = pcall(exists and store.update or store.create, document)
  if not ok then
    storage_error = "Could not save tasks. Please try again."
    return false
  end
  items, next_id, exists = candidate, candidate_next_id, true
  storage_error = nil
  return true
end

local function find(id)
  for index, item in ipairs(items) do if item.id == id then return index, item end end
end

local function on_event(event)
  if event.type == "text" and event.action == "todo.draft" then
    local length = code_points(event.value)
    if length and length <= MAX_TITLE then draft, draft_error = event.value, nil
    else draft_error = "Task titles can be at most 200 characters." end
    return
  end
  if event.type == "action" and event.action == "todo.reload" then load(); return end
  if not loaded then return end
  if event.type == "action" and event.action == "todo.add" then
    local title = string.match(draft, "^%s*(.-)%s*$")
    if title == "" then draft_error = "Enter a task title."
    elseif not valid_title(title) then draft_error = "Task titles can be at most 200 characters."
    elseif #items >= MAX_ITEMS then draft_error = "Your list has reached 100 tasks."
    elseif next_id >= MAX_ID then draft_error = "No more tasks can be added."
    else
      local candidate = copy_items()
      candidate[#candidate + 1] = { id = next_id, title = title, completed = false }
      if save(candidate, next_id + 1) then draft, draft_error = "", nil end
    end
    return
  end
  local toggle_id = event.type == "checked" and string.match(event.action, "^todo%.toggle%.(%d+)$")
  if toggle_id then
    local index, item = find(tonumber(toggle_id))
    if index and item.completed ~= event.value then
      local candidate = copy_items()
      candidate[index].completed = event.value
      save(candidate, next_id)
    end
    return
  end
  local delete_id = event.type == "action" and string.match(event.action, "^todo%.delete%.(%d+)$")
  if delete_id then
    local index = find(tonumber(delete_id))
    if index then
      local candidate = copy_items()
      table.remove(candidate, index)
      save(candidate, next_id)
    end
  end
end

local function render()
  local editable = loaded
  local children = {
    ui.text { text = "Tasks", style = "title" },
    ui.text { text = tostring(MAX_ITEMS - #items) .. " spaces left" },
    ui.textField { label = "What needs doing?", value = draft, action = "todo.draft", enabled = editable, error = draft_error },
    ui.button { text = "Add task", action = "todo.add", enabled = editable and #items < MAX_ITEMS }
  }
  if #items >= MAX_ITEMS then children[#children + 1] = ui.text { text = "Your list has reached 100 tasks." } end
  if storage_error then
    children[#children + 1] = ui.text { text = storage_error }
    if not loaded then children[#children + 1] = ui.button { text = "Reload saved tasks", action = "todo.reload" } end
  end
  if #items == 0 then children[#children + 1] = ui.text { text = "No tasks yet. Add one above." } end
  for _, item in ipairs(items) do
    children[#children + 1] = ui.card { children = {
      ui.checkbox { label = item.title, checked = item.completed, action = "todo.toggle." .. tostring(item.id), enabled = editable },
      ui.button { text = "Delete task", action = "todo.delete." .. tostring(item.id), enabled = editable }
    } }
  end
  return ui.column { gap = 16, children = children }
end

load()
return { render = render, onEvent = on_event }
