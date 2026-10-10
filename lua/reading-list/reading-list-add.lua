local CommonUi = featureModule("common-ui")

local function render(view)
  local children = CommonUi.children(view)
  if not view.loaded then
    return ui.column { gap = 16, children = children }
  end
  local message = view.message
  local screen = view.screen
  local selected = view.selected
  local draft = view.draft
  local requests = view.requests
  local deletion = view.deletion
  local errors = view.errors
  local text = CommonUi.text
  local button = CommonUi.button
  local card = CommonUi.card
  local find = view.find
  children[#children + 1] = button("Back to list / Cancel", "back", "quiet")
  local fields = {}
  for _, field in ipairs({ "url", "title", "tags", "note" }) do
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
  return ui.column { gap = 16, children = children }
end


return render
