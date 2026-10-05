print("Enter a sentence:")
local sentence = io.read() or ""
local count = 0

for _ in string.gmatch(sentence, "%S+") do
  count = count + 1
end

print("Words: " .. count)
