io.write("What is your name? ")
local name = io.read()

if name == nil or name == "" then
  print("Hello, mysterious Lua programmer!")
else
  print("Hello, " .. name .. "!")
end
