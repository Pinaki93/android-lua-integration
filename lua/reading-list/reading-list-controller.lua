local BaseController = featureModule("base-controller")
local renderView = featureModule("reading-list")
local baseController = BaseController.new()

local view = baseController.view
view.status, view.tag = "All", ""
view.page, view.tag_page = 1, 1

local function on_event(event)
  local action = string.sub(event.action, 9)

  if action == "dismissSnackbar" then
    view.message = nil
  elseif action == "reload" then
    baseController.load()
  elseif action == "add" then
    navigation.navigate("reading-list/add")
  elseif string.match(action, "^view%.") then
    navigation.navigate("reading-list/edit/" .. string.sub(action, 6))
  elseif action == "back" then
    navigation.back()
  elseif view.loaded then
    if string.match(action, "^status%.") then
      view.status, view.page = string.sub(action, 8), 1
    elseif action == "tag.all" then
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
  onResume = baseController.load,
}
