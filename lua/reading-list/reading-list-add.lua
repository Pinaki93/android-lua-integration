local CommonUi = featureModule("common-ui")

local function render(view)
  local children = CommonUi.children(view)
  local toolbar = ui.toolbar { title = view.screen == "detail" and "Edit Article" or (view.source and "Add Source" or "Add Article"), backAction = "reading.back" }
  local function scaffold() return CommonUi.scaffold(view, ui.column { gap = 16, children = children }, toolbar) end
  if not view.loaded then return scaffold() end
  local screen = view.screen
  local selected = view.selected
  local draft = view.draft
  local errors = view.errors
  local text = CommonUi.text
  local button = CommonUi.button
  local card = CommonUi.card
  local find = view.find
  local fields = {}
  for _, field in ipairs(view.source and { "url", "title" } or { "url", "title", "tags", "note" }) do
    fields[#fields + 1] = ui.textField { label = ({ url = "HTTPS URL", title = "Title", tags = "Tags (comma-separated)", note = "Note" })[field],
      value = draft[field], action = "reading.field." .. field, error = errors[field], enabled = field ~= "url" or screen == "add", multiline = field == "note",
      trailingIcon = field == "url" and screen == "add" and ui.iconButton { icon = "paste", label = "Paste URL", action = "reading.paste" } or nil }
  end
  children[#children + 1] = card(fields)
  local _, saved = find(selected)
  local changed = not saved or draft.title ~= saved.title or draft.tags ~= table.concat(saved.tags, ", ") or draft.note ~= saved.note
  children[#children + 1] = ui.button { text = "Save", action = "reading.save", enabled = changed }
  children[#children + 1] = text("Fetching a title and favicon contacts the article's website. Original articles require a connection.", "label", "secondary")
  if screen == "detail" then
    local _, item = find(selected)
    toolbar.overflow = {
      button(item.isRead and "Mark unread" or "Mark read", "toggle", "quiet"),
      button("Open original", "open", "quiet"), button("Retry title", "retry", "quiet"),
      button("Delete article", "delete", "destructive")
    }
  end
  return scaffold()
end

return render
