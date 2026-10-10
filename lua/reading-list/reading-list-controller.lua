local BaseController = featureModule("base-controller")
local renderView = featureModule("reading-list")
local articleController = BaseController.new()
local sourceController
local baseController = articleController

local view = baseController.view
view.status, view.tag = "All", ""
view.page, view.tag_page = 1, 1

-- The add session closes on navigation; finish pending source metadata in the list session.
local sourceRequests = {}
local function load_sources()
  sourceController.load()
  if not sourceController.view.loaded then return end
  for _, item in ipairs(sourceController.view.articles) do
    if item.metadataState == "pending" and not sourceRequests[item.id] then
      local id = item.id
      sourceRequests[id] = true
      reading.fetch(item.url, function(result)
        local _, failure = sourceController.interactor.updateMetadata(id, result)
        sourceController.refresh(failure, failure and "Could not save sources. Please try again.")
        sourceRequests[id] = nil
      end)
    end
  end
end

local function show_articles()
  view.sheetSelected = nil
  baseController = articleController
  view = baseController.view
end

local function on_event(event)
  local action = string.sub(event.action, 9)

  if action == "dismissSnackbar" then
    view.message = nil
  elseif action == "reload" then
    if view.source then load_sources() else baseController.load() end
  elseif action == "sources" then
    sourceController = sourceController or BaseController.new(true)
    baseController = sourceController
    view = baseController.view
    view.page = view.page or 1
    load_sources()
  elseif action == "add" then
    navigation.navigate(view.source and "reading-list/add/source" or "reading-list/add")
  elseif string.match(action, "^view%.") then
    local _, article = view.find(string.sub(action, 6))
    view.sheetSelected = article and article.id or nil
  elseif action == "dismissSheet" then
    view.sheetSelected = nil
  elseif action == "read" then
    local _, article = view.find(view.sheetSelected)
    view.sheetSelected = nil
    if article and not pcall(reading.open, article.url) then
      view.message = "Could not open original. A browser and connection are needed."
    end
  elseif action == "edit" and not view.source then
    local selected = view.sheetSelected
    view.sheetSelected = nil
    if selected then navigation.navigate("reading-list/edit/" .. selected) end
  elseif action == "delete" then
    local _, article = view.find(view.sheetSelected)
    if article then
      baseController.scaffold.showAlert(ui.alert {
        title = view.source and "Delete source?" or "Delete article?", subtitle = article.title .. (view.source and "\nThis removes the saved source." or "\nThis removes the saved entry and note."),
        positive = ui.button { text = "Delete", action = "reading.confirmDelete", style = "destructive" },
        negative = ui.button { text = "Cancel", action = "reading.cancelDelete", style = "quiet" },
        dismissAction = "reading.cancelDelete",
      })
    end
  elseif action == "cancelDelete" then
    baseController.scaffold.dismissAlert()
  elseif action == "confirmDelete" then
    local ok, failure = baseController.interactor.change(view.sheetSelected, "delete")
    baseController.refresh(failure, failure and "Could not save articles. Please try again.")
    if ok then
      view.sheetSelected = nil
      baseController.scaffold.dismissAlert()
    end
  elseif action == "back" then
    if view.source then show_articles() else navigation.back() end
  elseif string.match(action, "^status%.") then
    if view.source then show_articles() end
    view.status, view.page = string.sub(action, 8), 1
  elseif view.loaded then
    if action == "tag.all" then
      view.tag, view.page = "", 1
    elseif string.match(action, "^tag%.") then
      local list = {}
      for _, item in ipairs(view.articles) do
        for _, value in ipairs(item.tags) do
          list[#list + 1] = value
        end
      end
      view.tag, view.page = list[tonumber(string.sub(action, 5))] or "", 1
    elseif action == "nextTags" then
      view.tag_page = view.tag_page + 1
    elseif action == "previousTags" then
      view.tag_page = view.tag_page > 1 and view.tag_page - 1 or 1
    elseif action == "next" then
      view.page = view.page + 1
    elseif action == "previous" then
      view.page = view.page > 1 and view.page - 1 or 1
    end
  end
end

return {
  render = function() return baseController.render(renderView) end,
  onEvent = on_event,
  onResume = function()
    if view.source then load_sources() else baseController.load() end
  end,
}
