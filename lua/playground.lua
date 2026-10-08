local function on_event(event)
  if event.action == "playground.dashboard" then navigation.navigate("dashboard") end
  if event.action == "playground.todo" then navigation.navigate("todo") end
end

local function render()
  return ui.column { gap = 16, children = {
    ui.text { text = "Lua Playground", style = "title" },
    ui.text { text = "Small, explicit Lua capabilities you can try on this device." },
    ui.button { text = "Lua Compose dashboard", action = "playground.dashboard" },
    ui.button { text = "Persistent todo form", action = "playground.todo" }
  } }
end

return { render = render, onEvent = on_event }
