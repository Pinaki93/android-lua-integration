local CommonUi = {}

function CommonUi.text(value, style, tone)
  return ui.text { text = value, style = style, tone = tone }
end

function CommonUi.button(label, action, style)
  return ui.button { text = label, action = "reading." .. action, style = style }
end

function CommonUi.card(children, style, action)
  return ui.card {
    style = style,
    action = action and "reading." .. action,
    children = { ui.column { gap = 12, children = children } },
  }
end

function CommonUi.filter(label, action, selected)
  return CommonUi.button(label .. (selected and " (selected)" or ""), action, selected and "selected" or "filter")
end

function CommonUi.children(view)
  local children = { CommonUi.text("Reading list", "heading") }
  if view.message and view.loaded then
    children[#children + 1] = CommonUi.card({ CommonUi.text("Update", "title"), CommonUi.text(view.message) }, "lightOrange")
  end
  if not view.loaded then
    children[#children + 1] = CommonUi.card({
      CommonUi.text("Your library needs attention", "title"),
      CommonUi.text(view.message),
      CommonUi.button("Reload saved articles", "reload"),
    }, "outlined")
  end
  return children
end

return CommonUi
