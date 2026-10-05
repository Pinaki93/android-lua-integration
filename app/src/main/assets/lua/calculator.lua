print("Enter a number, an operator (+, -, *, /), and another number on separate lines:")
local left = tonumber(io.read())
local operator = io.read()
local right = tonumber(io.read())

if left == nil or right == nil then
  print("Both values must be numbers.")
elseif operator == "+" then
  print(left + right)
elseif operator == "-" then
  print(left - right)
elseif operator == "*" then
  print(left * right)
elseif operator == "/" and right ~= 0 then
  print(left / right)
elseif operator == "/" then
  print("Cannot divide by zero.")
else
  print("Unknown operator: " .. tostring(operator))
end
