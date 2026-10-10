local BaseController = featureModule("base-controller")
local renderView = featureModule("reading-list-add")
local id = navigation.arguments.id
local base = BaseController.new()
local interactor, view = base.interactor, base.view
local find_article = view.find
local messages = {
  quota = "Storage is full. Shorten content or delete entries.",
  save = "Could not save articles. Please try again.",
  url = "Enter an absolute HTTPS URL without credentials, on port 443 (maximum 2,048 characters).",
  title = "Use at most 300 characters.",
  note = "Use at most 4,000 characters.",
  tags = "Enter up to five comma-separated tags, at most 30 characters each.",
}

local function refresh(failure)
  base.refresh(failure, messages[failure])
  if failure and base.scaffold.alert then
    base.scaffold.alert.subtitle = messages[failure]
  end
end

view.screen = "add"
view.errors, view.requests = {}, {}
view.draft = { url = "", title = "", tags = "", note = "" }
view.deletion, view.preview_token = false, 0
local function load_articles()
  base.load()
  if view.loaded then view.requests = {} end
end

local function show_details(article)
  view.selected, view.screen, view.deletion, view.errors = article.id, "detail", false, {}
  view.draft = {
    url = article.url,
    title = article.title,
    tags = table.concat(article.tags, ", "),
    note = article.note,
  }
end

local function apply_metadata(id, result)
  local updated, failure, current = interactor.updateMetadata(id, result)
  refresh(failure)
  if updated then
    if view.selected == id and view.draft.title == current.title then
      view.draft.title = updated.title
    end
    if result.error == "redirect" then
      view.message = "Website redirected. Save its final HTTPS URL manually."
    elseif result.error then
      view.message = "Title unavailable. Your saved entry is usable offline; Retry when ready."
    end
  end
  view.requests[id] = nil
end

-- A request token lets the callback ignore a response superseded by a newer request.
local function fetch_metadata(id)
  local _, article = find_article(id)
  if not article then
    return
  end
  local token = (view.requests[id] or 0) + 1
  view.requests[id] = token
  reading.fetch(article.url, function(result)
    if view.requests[id] == token then
      apply_metadata(id, result)
    end
  end)
end

local function open_initial_screen()
  if view.loaded and not id then
    view.screen = "add"
  elseif view.loaded and id then
    local _, article = find_article(id)
    if article then
      show_details(article)
    else
      view.loaded, view.message = false, "This article no longer exists."
    end
  end
end

local function update_draft(event, action)
  local field = string.match(action, "^field%.(%w+)$")
  if field then
    view.draft[field], view.errors[field] = event.value, nil
    if field == "url" and view.screen == "add" then
      -- URL edits invalidate old previews, even if their network response arrives later.
      view.preview_token = view.preview_token + 1
      local token, previous = view.preview_token, view.preview
      view.preview = nil
      if previous and previous.cancel and not previous.id then
        previous.cancel()
      end
      if previous and previous.title == view.draft.title then
        view.draft.title = ""
      end
      local ok, url = pcall(reading.normalize, event.value)
      if ok then
        local pending = { url = url, pending = true }
        view.preview = pending
        pending.cancel = reading.fetch(url, function(result)
          if pending.id then
            if view.requests[pending.id] ~= -token then
              return
            end
          elseif token ~= view.preview_token then
            return
          end
          local saved_id = pending.id
          local result_preview = {
            url = url,
            title = not result.error and result.title or nil,
            favicon = result.favicon,
            error = result.error,
          }
          if token == view.preview_token then
            view.preview = result_preview
            if view.screen == "add" and view.draft.title == "" and view.preview.title then
              view.draft.title = view.preview.title
            end
          end
          if saved_id then
            apply_metadata(saved_id, result)
          end
        end)
      end
    end
  end
end

local function save_form()
  local form, failures = interactor.validate(view.draft, view.selected)
  view.errors = {}
  for field, failure in pairs(failures or {}) do view.errors[field] = messages[failure] end
  if not form then return end
  local adding = view.screen == "add"
  local article, failure
  if adding then
    local metadata = nil
    if view.preview and not view.preview.pending then metadata = view.preview end
    article, failure = interactor.add(form, metadata)
  else
    article, failure = interactor.edit(view.selected, form)
  end
  refresh(failure)
  if not article then return end
  show_details(article)
  if failure == "duplicate" then
    view.message = "Already saved"
  elseif adding then
    if view.preview and view.preview.pending then
      view.preview.id = article.id
      -- Negative tokens identify a draft request that now belongs to a saved article.
      view.requests[article.id] = -view.preview_token
    elseif not view.preview then
      fetch_metadata(article.id)
    end
  end
end

local function handle_article_action(action)
  local _, article = find_article(view.selected)
  if not article then
    return
  end
  if action == "toggle" then
    local _, failure = interactor.change(view.selected, "toggle")
    refresh(failure)
  elseif action == "retry" then
    local ok, failure = interactor.change(view.selected, "retry")
    refresh(failure)
    if ok then
      fetch_metadata(view.selected)
    end
  elseif action == "open" then
    local ok = pcall(reading.open, article.url)
    if not ok then
      view.message = "Could not open original. A browser and connection are needed."
    end
  elseif action == "delete" then
    view.deletion = true
    base.scaffold.showAlert(ui.alert {
      title = "Delete article?", subtitle = article.title .. "\n" .. "This removes the saved entry and note.",
      positive = ui.button { text = "Delete", action = "reading.confirmDelete", style = "destructive" },
      negative = ui.button { text = "Cancel", action = "reading.cancelDelete", style = "quiet" },
      dismissAction = "reading.cancelDelete",
    })
  elseif action == "confirmDelete" and view.deletion then
    local ok, failure = interactor.change(view.selected, "delete")
    refresh(failure)
    if ok then
      view.requests[view.selected] = nil
      base.scaffold.dismissAlert()
      navigation.back()
    end
  end
end

local function on_event(event)
  local action = string.sub(event.action, 9)
  if event.type == "text" then
    update_draft(event, action)
    return
  end
  if action == "dismissSnackbar" then
    view.message = nil
    return
  end
  if action == "reload" then
    load_articles()
    open_initial_screen()
    return
  end
  if action == "back" then
    navigation.back()
    return
  end
  if not view.loaded then return end
  if action == "add" then
    if view.preview and view.preview.cancel and not view.preview.id then
      view.preview.cancel()
    end
    view.screen, view.selected, view.errors, view.message = "add", nil, {}, nil
    view.preview_token, view.preview = view.preview_token + 1, nil
    view.draft = { url = "", title = "", tags = "", note = "" }
  elseif action == "paste" and view.screen == "add" then
    local ok, value = pcall(reading.clipboard)
    if not ok then
      view.message = "Could not read clipboard. Try pasting into the URL field."
    elseif reading.trim(value) == "" then
      view.message = "No text to paste. Copy a URL first."
    elseif not pcall(reading.normalize, value) then
      view.message = "Clipboard does not contain a valid HTTPS URL."
    else
      view.message = nil
      on_event { type = "text", action = "reading.field.url", value = value }
    end
  elseif action == "save" then
    save_form()
  elseif string.match(action, "^view%.") then
    local _, article = find_article(string.sub(action, 6))
    if article then
      show_details(article)
      view.message = nil
    end
  elseif action == "cancelDelete" then
    view.deletion = false
    base.scaffold.dismissAlert()
  else
    handle_article_action(action)
  end
end

open_initial_screen()
return {
  render = function() return base.render(renderView) end,
  onEvent = on_event,
}
