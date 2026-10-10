-- Article rules and persistence; no screen state or session callbacks.
local ReadingInteractor = {}

function ReadingInteractor.new()
  local articles = {}
  local loaded = false
  local exists = false
  local store = nil

  local function trim(value)
    return reading.trim(value)
  end

  local function find_article(id, list)
    for index, article in ipairs(list or articles) do
      if article.id == id then
        return index, article
      end
    end
  end

  -- Mutate a copy so a failed storage write cannot change the current library.
  -- Tags are shared here because updates replace the tag list rather than changing it in place.
  local function copy_articles()
    local result = {}
    for index, article in ipairs(articles) do
      local item = {}
      for key, value in pairs(article) do
        item[key] = value
      end
      result[index] = item
    end
    return result
  end

  local function parse_tags(value)
    local result, seen = {}, {}
    for part in string.gmatch(value .. ",", "(.-),") do
      local clean = trim(part)
      local key = reading.lower(clean)
      if clean ~= "" and not seen[key] then
        if reading.length(clean) > 30 then
          return nil
        end
        seen[key] = true
        result[#result + 1] = clean
      end
    end
    if #result > 5 then
      return nil
    end
    return result
  end

  local function is_array(value)
    if not reading.isArray(value) then
      return false
    end
    for key in pairs(value) do
      if type(key) ~= "number" or key % 1 ~= 0 or key < 1 or key > #value then
        return false
      end
    end
    return true
  end

  local function has_expected_keys(value, allowed)
    if type(value) ~= "table" then
      return false
    end
    for key in pairs(value) do
      if not allowed[key] then
        return false
      end
    end
    for key in pairs(allowed) do
      if key ~= "favicon" and value[key] == nil then
        return false
      end
    end
    return true
  end

  -- Reject the whole document on invalid data; never silently discard saved entries.
  local function is_valid_document(document)
    if not has_expected_keys(document, { schemaVersion = true, articles = true })
        or document.schemaVersion ~= 1
        or not is_array(document.articles) then
      return false
    end
    local ids, urls = {}, {}
    for _, item in ipairs(document.articles) do
      if not has_expected_keys(item, {
        id = true,
        url = true,
        title = true,
        titleSource = true,
        tags = true,
        note = true,
        isRead = true,
        createdAt = true,
        metadataState = true,
        favicon = true,
      }) then
        return false
      end
      if item.favicon ~= nil and (type(item.favicon) ~= "string" or not reading.validFavicon(item.favicon)) then
        return false
      end
      if type(item.id) ~= "string" or not string.match(item.id, "^[%w-]+$") or #item.id > 100 or ids[item.id] then
        return false
      end
      local ok, normalized = pcall(reading.normalize, item.url)
      if not ok or normalized ~= item.url or urls[item.url] then
        return false
      end
      if type(item.title) ~= "string"
          or trim(item.title) == ""
          or reading.length(item.title) > (item.titleSource == "fallback" and 2048 or 300) then
        return false
      end
      if item.titleSource ~= "user"
          and item.titleSource ~= "metadata"
          and item.titleSource ~= "fallback" then
        return false
      end
      if item.titleSource == "fallback" and item.title ~= item.url then
        return false
      end
      if type(item.note) ~= "string" or reading.length(item.note) > 4000 or type(item.isRead) ~= "boolean" then
        return false
      end
      if type(item.createdAt) ~= "number"
          or item.createdAt % 1 ~= 0
          or item.createdAt < 0
          or item.createdAt > 9007199254740991 then
        return false
      end
      if item.metadataState ~= "pending"
          and item.metadataState ~= "available"
          and item.metadataState ~= "failed" then
        return false
      end
      if not is_array(item.tags) or #item.tags > 5 then
        return false
      end
      local seen = {}
      for _, value in ipairs(item.tags) do
        if type(value) ~= "string"
            or value == ""
            or trim(value) ~= value
            or reading.length(value) > 30
            or seen[reading.lower(value)] then
          return false
        end
        seen[reading.lower(value)] = true
      end
      ids[item.id], urls[item.url] = true, true
    end
    return true
  end

  local function load_articles()
    local ok, document = pcall(function()
      store = storage.open("reading-list.json")
      return store.read()
    end)
    if not ok or (document ~= nil and not is_valid_document(document)) then
      loaded = false
      return false, "load"
    end
    articles = document and document.articles or {}
    exists = document ~= nil
    loaded = true
    return true
  end

  local function save_articles(candidate)
    if not loaded then return false, "load" end
    local ok, failure = pcall(exists and store.update or store.create, { schemaVersion = 1, articles = candidate })
    if not ok then
      if string.find(tostring(failure), "quota exceeded", 1, true) then
        return false, "quota"
      else
        return false, "save"
      end
    end
    articles = candidate
    exists = true
    return true
  end

  local function validate_article(input, selected)
    local errors = {}
    local ok, url = pcall(reading.normalize, input.url)
    if not ok then
      errors.url = "url"
    end
    local title = trim(input.title)
    local _, current = find_article(selected)
    local unchanged_fallback = current and current.titleSource == "fallback" and title == current.title
    if reading.length(title) > 300 and not unchanged_fallback then
      errors.title = "title"
    end
    if reading.length(input.note) > 4000 then
      errors.note = "note"
    end
    local normalized_tags = parse_tags(input.tags)
    if not normalized_tags then
      errors.tags = "tags"
    end
    if next(errors) then
      return nil, errors
    end
    return { url = url, title = title, tags = normalized_tags, note = input.note }
  end

  local function add(form, metadata)
    for _, article in ipairs(articles) do
      if article.url == form.url then return article, "duplicate" end
    end
    local article = {
      id = reading.id(),
      url = form.url,
      title = form.title ~= "" and form.title or form.url,
      titleSource = metadata and metadata.title == form.title and "metadata"
          or form.title ~= "" and "user" or "fallback",
      favicon = metadata and metadata.favicon,
      tags = form.tags,
      note = form.note,
      isRead = false,
      createdAt = reading.now(),
      metadataState = metadata and (metadata.error and "failed" or "available") or "pending",
    }
    local candidate = copy_articles()
    candidate[#candidate + 1] = article
    local ok, failure = save_articles(candidate)
    if ok then return article end
    return nil, failure
  end

  local function edit(selected, form)
    local index, current = find_article(selected)
    if not index then
      return
    end
    local candidate = copy_articles()
    local item = candidate[index]
    item.tags, item.note = form.tags, form.note
    if form.title ~= current.title then
      item.title = form.title ~= "" and form.title or item.url
      item.titleSource = form.title ~= "" and "user" or "fallback"
    end
    local ok, failure = save_articles(candidate)
    if ok then return item end
    return nil, failure
  end

  local function update_metadata(id, result)
    local index, current = find_article(id)
    if not index then
      return
    end
    local candidate = copy_articles()
    local updated = candidate[index]
    updated.favicon = result.favicon or updated.favicon
    updated.metadataState = result.error and "failed" or "available"
    if not result.error and current.titleSource == "fallback" and current.title == current.url then
      updated.title, updated.titleSource = result.title, "metadata"
    end
    local ok, failure = save_articles(candidate)
    if ok then return updated, nil, current end
    return nil, failure
  end

  local function change(id, action)
    local index, article = find_article(id)
    if not article then return false end
    local candidate = copy_articles()
    if action == "toggle" then
      candidate[index].isRead = not article.isRead
    elseif action == "retry" then
      candidate[index].metadataState = "pending"
    elseif action == "delete" then
      table.remove(candidate, index)
    else
      return false
    end
    return save_articles(candidate)
  end

  return {
    load = load_articles,
    articles = function() return articles end,
    find = find_article,
    validate = validate_article,
    add = add,
    edit = edit,
    updateMetadata = update_metadata,
    change = change,
  }
end

return ReadingInteractor
