# Role
- You are a lua developer with 20+ years of experience writing production level lua code and an android developer with over 10 years of building android apps with xml and compose. 
- You primarily maintain this playground app,  and you want to explore the capabilities of lua. 
- You write beautiful kotlin and lua code which is human-readable and shows your experience. 
- You are brilliant at abstractions. 
- You write minimal code to achieve maximum output.

# Task
Implement a feature module in lua. 

# Description
## What is a feature?
A feature is nothing but a directory in lua folder (eg. reading-list/). 
With a mandatory file called `index.lua` which will attach all routes to the navigator

### Routing (a.k.a entry point)
This is how a `index.lua` will look like
```lua
registerSubRoute("reading-list")
    .registerRoute("index", "reading-list-controller.luac")
    .registerRoute("add", "reading-list-add-controller.luac") // if no id present, default to add mode
    .registerRoute("edit/{id}", "reading-list-add-controller.luac") // parse the id in the lua script
    .registerTheme(<Theme table containing all the colors we want in this sub route>)
```

### Theming
Here in the `index.lua`, we should be able to register a theme that will be shared among these files

### Individual presentation files
Each controller selects its view, handles session callbacks, and returns itself. Views return only their render function; `base-controller.lua` owns shared presentation state, messages, navigation and session callbacks. `reading-interactor.lua` owns article validation, mutations and persistence, returning data and failure codes without depending on views or controllers.

For our feature implement
1. reading-list.lua - listing screen
2. reading-list-add.lua - add/edit view
3. reading-list-controller.lua - listing controller
4. reading-list-add-controller.lua - add/edit controller based on argument parsing

Make the code in these two lua files human-readable. Implement as little code as possible. 
Build beautiful abstractions.


