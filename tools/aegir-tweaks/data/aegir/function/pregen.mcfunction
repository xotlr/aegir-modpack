# Pre-generate the whole finite world in the background right away, so terrain (and Distant Horizons'
# far view, built from these chunks) is ready as soon as possible. Square radius 4096 = the 8192 world.
# Chunky resumes after restarts (config/chunky/config.json continueOnRestart).
chunky world minecraft:overworld
chunky shape square
chunky center 0 0
chunky radius 4096
chunky start
