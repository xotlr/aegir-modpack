# Base max health 10 = 5 hearts. Bonuses from other mods still stack on top of the base.
# Only writes when it differs, so the attribute isn't re-synced every second.
execute as @a store result score @s aegir_hp run attribute @s minecraft:generic.max_health base get
execute as @a unless score @s aegir_hp matches 10 run attribute @s minecraft:generic.max_health base set 10
schedule function aegir:max_health 20t replace
