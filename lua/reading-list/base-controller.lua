-- Shared loading, error messages and rendering.
local ReadingInteractor = featureModule("reading-interactor")
local BaseController = {}

function BaseController.new(source)
  local interactor = ReadingInteractor.new(source)
  local view = { source = source, articles = {}, loaded = false, message = nil }
  view.find = interactor.find
  local scaffold = {}
  function scaffold.showSnackbar(message) view.message = message end
  function scaffold.showAlert(alert) scaffold.alert = alert end
  function scaffold.dismissAlert() scaffold.alert = nil end
  view.scaffold = scaffold

  local function refresh(failure, message)
    view.articles = interactor.articles()
    scaffold.showSnackbar(failure == "load"
      and "Could not load saved articles. Reload to recover; your document has not been reset."
      or message)
  end

  local function load()
    local failure
    view.loaded, failure = interactor.load()
    refresh(failure)
  end

  load()

  return {
    scaffold = scaffold,
    interactor = interactor,
    view = view,
    refresh = refresh,
    load = load,
    render = function(presentation) return presentation(view) end,
  }
end

return BaseController
