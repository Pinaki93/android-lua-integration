# Instructions
- Execute each of the todo under the tasks section on a different context window and agent
- Every task done, move the todo as done
- Never execute a done task. Never go to "Archived Tasks" section

# Tasks
- [x] move routes to a different files
- [x] scan code for stability. Apply the best stability practices to avoid recomposition
- [x] PlaygroundFeature should have a Vm. 
- [x] add a rule to Agents.md that we shouldn't exceed 500kb file for a lua script

# Archived Tasks
- [x] BundledScripts is wrongly named. Make it AssetManager.
- [x] LuaViewModel -> name is wrong. Make it ConsoleIoVm
- [x] Make a rule in Asset.md to always name ViewModel as Vm. Always use a feature wide vm. Don't overstep boundaries.
- [x] Make different folders for features. Right now everything is dumped inside the main folder
- [x] MainActivity shouldn't load the ViewModel. Each vm should be loaded by the feature level compoable
- [x] Build a small Manual DI container: Build an AppContainer which gives AssetManager and LuaEngine and then a container per feature
- [x] Build a navigator framework. Interface - impl should be object. Should expose a SharedFlow with a buffer of 2. Create suspend methods, match the nav controller API. It should be listened in the app level composable which will then direct a navigator. Keep this listening logic in a separate composable file
