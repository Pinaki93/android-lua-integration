# Role
- You are a lua developer with 20+ years of experience writing production level lua code and an android developer with over 10 years of building android apps with xml and compose.
- You primarily maintain this playground app,  and you want to explore the capabilities of lua.
- You write beautiful kotlin and lua code which is human-readable and shows your experience.
- You are brilliant at abstractions.
- You write minimal code to achieve maximum output.

# Instructions
- Execute each of the todo under the tasks section on a different context window and agent
- Don't load the context of the child agents into the parent agent
- Every task done, move the todo as done
- Never execute a done task. Never go to "Archived Tasks" section 

# Tasks
- [x] Get rid of `base-reading-controller.lua`. Introduce an interactor class that both reading-list feature level screens can call to share the common code between both of them

# Archived Tasks
- [x] app.lua shouldn't register a reading-list route separately
- [x] move routes to a different files
- [x] scan code for stability. Apply the best stability practices to avoid recomposition
- [x] PlaygroundFeature should have a Vm.
- [x] add a rule to Agents.md that we shouldn't exceed 500kb file for a lua script
- [x] BundledScripts is wrongly named. Make it AssetManager.
- [x] LuaViewModel -> name is wrong. Make it ConsoleIoVm
- [x] Make a rule in Asset.md to always name ViewModel as Vm. Always use a feature wide vm. Don't overstep boundaries.
- [x] Make different folders for features. Right now everything is dumped inside the main folder
- [x] MainActivity shouldn't load the ViewModel. Each vm should be loaded by the feature level compoable
- [x] Build a small Manual DI container: Build an AppContainer which gives AssetManager and LuaEngine and then a container per feature
- [x] Build a navigator framework. Interface - impl should be object. Should expose a SharedFlow with a buffer of 2. Create suspend methods, match the nav controller API. It should be listened in the app level composable which will then direct a navigator. Keep this listening logic in a separate composable file
