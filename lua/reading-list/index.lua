registerSubRoute("reading-list")
  .registerRoute("index", "reading-list-controller.luac")
  .registerRoute("add", "reading-list-add-controller.luac")
  .registerRoute("edit/{id}", "reading-list-add-controller.luac")
  .registerTheme { primary = "#176047", onPrimary = "#FFFFFF", toolbar = "#EEECE6" }
