# Runs once per world (first load), so later /gamerule changes are kept.
# Renourished Delight, Valheim-style food-as-health: start at 6 HP (3 hearts), food raises max health for a while.
gamerule renourisheddelight:playerStartingHearts 6
gamerule renourisheddelight:maxConsumableFood 3
gamerule renourisheddelight:doStarvation false
gamerule renourisheddelight:doSleepFoodDrain false
gamerule renourisheddelight:doNourishment false
gamerule renourisheddelight:doNutritionDecay false
scoreboard players set #world_init aegir 1
