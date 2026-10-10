-- Shared loading, error messages and rendering.
local ReadingInteractor = featureModule("reading-interactor")
local BaseController = {}

function BaseController.new()
  local interactor = ReadingInteractor.new()
  local view = { articles = {}, loaded = false, message = nil }
  view.find = interactor.find

  local function refresh(failure, message)
    view.articles = interactor.articles()
    view.message = failure == "load"
      and "Could not load saved articles. Reload to recover; your document has not been reset."
      or message
  end

  local function load()
    local failure
    view.loaded, failure = interactor.load()
    refresh(failure)
  end

  load()

  return {
    interactor = interactor,
    view = view,
    refresh = refresh,
    load = load,
    render = function(presentation) return presentation(view) end,
  }
end

return BaseController
