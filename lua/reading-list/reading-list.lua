local CommonUi = featureModule("common-ui")

local function render(view)
	local children = CommonUi.children(view)
    local toolbar = ui.toolbar { title = "Reading List", backAction = view.source and "reading.back" or nil, children = {
      ui.iconButton { icon = "add", label = view.source and "Add source" or "Add article", action = "reading.add", enabled = view.loaded }
    } }
    local _, selected = view.find(view.sheetSelected)
    local sheetActions = { CommonUi.button(view.source and "Open" or "Read", "read", "primary") }
    if not view.source then sheetActions[#sheetActions + 1] = CommonUi.button("Edit", "edit", "filter") end
    sheetActions[#sheetActions + 1] = CommonUi.button("Delete", "delete", "destructive")
    local bottomSheet = selected and ui.bottomSheet {
      dismissAction = "reading.dismissSheet", cornerRadius = 32,
      content = ui.column { gap = 12, children = {
        CommonUi.text(view.source and "READING SOURCE" or "YOUR READING QUEUE", "label", "gold"),
        CommonUi.text(selected.title, "title"),
        CommonUi.text(reading.hostname(selected.url), "body", "secondary"),
        ui.row { gap = 12, wrap = true, children = sheetActions },
      } },
    } or nil
    local function scaffold() return CommonUi.scaffold(view, ui.column { gap = 16, children = children }, toolbar, bottomSheet) end

	if not view.loaded then
		return scaffold()
	end

  local statuses = { CommonUi.filter("source", "sources", view.source) }
  for _, value in ipairs({ "All", "Unread", "Read" }) do
    statuses[#statuses + 1] = CommonUi.filter(value, "status." .. value, not view.source and view.status == value)
  end
  children[#children + 1] = ui.row { gap = 8, wrap = true, children = statuses }

  if view.source then
    if #view.articles == 0 then
      children[#children + 1] = CommonUi.text("No sources yet. Add a source in the toolbar.")
    end
    local count = #view.articles
    local lastPage = (count - count % 20) / 20 + (count % 20 > 0 and 1 or 0)
    if lastPage < 1 then lastPage = 1 end
    if view.page > lastPage then view.page = lastPage end
    local lastIndex = view.page * 20 < count and view.page * 20 or count
    for index = (view.page - 1) * 20 + 1, lastIndex do
      local item = view.articles[index]
      children[#children + 1] = ui.listItem { key = "source." .. item.id, children = {
        CommonUi.card({ ui.row { gap = 12, children = {
          ui.image { url = item.favicon and "data:image/png;base64," .. item.favicon, label = "Website icon", width = 32, height = 32 },
          CommonUi.text(item.title, "title"),
        } } }, "default", "view." .. item.id)
      } }
    end
    if view.page > 1 then children[#children + 1] = CommonUi.button("Previous page", "previous", "quiet") end
    if view.page < lastPage then children[#children + 1] = CommonUi.button("Next page", "next", "quiet") end
    return scaffold()
  end

	local articles = view.articles
	local status = view.status
	local tag = view.tag
	local page = view.page
	local tag_page = view.tag_page
	local unread = 0

	local text = CommonUi.text
	local button = CommonUi.button
	local card = CommonUi.card
	local filter = CommonUi.filter

	for _, item in ipairs(articles) do
		if not item.isRead then
			unread = unread + 1
		end
	end

	children[#children + 1] = text(#articles .. " saved · " .. unread .. " unread", "label", "secondary")

	children[#children + 1] = text("Tag filter: " .. (tag == "" and "All tags" or tag))
	local tag_filters = { filter("All tags", "tag.all", tag == "") }
	local seen, position, tag_count = {}, 0, 0
	for _, item in ipairs(articles) do
		for _, value in ipairs(item.tags) do
			position = position + 1
			if not seen[reading.lower(value)] then
				tag_count = tag_count + 1
				if tag_count > (tag_page - 1) * 20 and tag_count <= tag_page * 20 then
					tag_filters[#tag_filters + 1] = filter("Tag: " .. value, "tag." .. position, reading.lower(tag) == reading.lower(value))
				end
				seen[reading.lower(value)] = true
			end
		end
	end
	children[#children + 1] = ui.row { gap = 8, wrap = true, children = tag_filters }
	if tag_page > 1 then
		children[#children + 1] = button("Previous tags", "previousTags", "quiet")
	end
	if tag_page * 20 < tag_count then
		children[#children + 1] = button("More tags", "nextTags", "quiet")
	end

	local visible = {}
	for _, item in ipairs(articles) do
		local matches = tag == ""
		for _, value in ipairs(item.tags) do
			if reading.lower(value) == reading.lower(tag) then
				matches = true
			end
		end
		if matches and (status == "All" or status == "Read" and item.isRead or status == "Unread" and not item.isRead) then
			visible[#visible + 1] = item
		end
	end
	table.sort(
		visible,
		function(a, b)
			return a.createdAt > b.createdAt or a.createdAt == b.createdAt and a.id > b.id
		end
	)

	if #visible == 0 then
		children[#children + 1] = card({ text(#articles == 0 and "A little space for good reading" or "Try another view", "title"),
			text(#articles == 0 and "No articles yet. Add one to your queue." or "No articles match these filters.", "body", "secondary"),
			#articles == 0 and text("Use Add article in the toolbar.", "label", "secondary") or ui.row { gap = 8, wrap = true, children = {
				button("All articles", "status.All", "quiet"), button("All tags", "tag.all", "quiet") } }
		}, #articles == 0 and "subtle" or "outlined")
	end

	-- shortcut: render 20 entries per page to stay within Lua UI tree limits; upgrade to windowed lists if needed.
	local last_page = (#visible - #visible % 20) / 20 + (#visible % 20 > 0 and 1 or 0)
	if last_page < 1 then
		last_page = 1
	end
	if page > last_page then
		page = last_page
	end
	local last_index = page * 20 < #visible and page * 20 or #visible
	for index = (page - 1) * 20 + 1, last_index do
		local item = visible[index]
		children[#children + 1] = ui.listItem { key = "reading." .. item.id, children = {
			card({ text(item.isRead and "Read" or "Unread", item.isRead and "label" or "badge", "secondary"),
				ui.row { gap = 12, children = { ui.image { url = item.favicon and "data:image/png;base64," .. item.favicon, label = "Website icon", width = 32, height = 32 }, text(item.title, "title") } }, text(reading.hostname(item.url), "label", "secondary"),
				text(#item.tags > 0 and table.concat(item.tags, " · ") or "No tags", "label", "gold")
			}, "default", "view." .. item.id)
		} }
	end

	if page > 1 then
		children[#children + 1] = button("Previous page", "previous", "quiet")
	end
	if page * 20 < #visible then
		children[#children + 1] = button("Next page", "next", "quiet")
	end
	view.page = page
	return scaffold()
end

return render
