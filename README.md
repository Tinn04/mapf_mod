# MAPF Mod (Fabric • Minecraft 1.21.10)

This mod aims to add **cooperative pathfinding** to specific Minecraft mobs using Multi-Agent Path Finding (MAPF) concepts.

Quick Info (in-game)

Reset (this is always recommended before a new experiment)
/mapf reset

Build the arena. Best practice is to press F3 and check your Y-coordinate.
/mapf arena Y (grid size)
So if you do /mapf arena 63 33, it will generate a 33x33 arena on Y = 63.
You can then add your obstacles afterwards.

Next you have to pick your agents. Summon villagers with either:
/summon villager
Or just use a spawn egg
To create an agent use the following command:
/mapf agent @e[type=minecraft:villager,limit=1,sort=nearest]
The command above will turn the villager closest to you into an agent
You can also do /mapf agent UUID
Of course that would be the UUID of the villager which you can get if 
you're looking at the villager with your crosshair at the time of writing
the command.
If you wish to create multiple agents you can do this multiple times.
Once a villager has turned into an agent it will stop moving.

To create a goal use the following command:
/mapf goal (agent) x z
An example could be:
/mapf goal Agent1 55 85
/mapf goal Agent2 85 86
Just make sure your X and Z are within the bounds of the arena

Then you can choose your algorithm (whca, cbs, single)
For "/mapf algo whca" you can also use the following commands to adjust:
/mapf window 80 # WHCA* lookahead window (measured in ticks. 20 ticks = 1 sec)
/mapf replan 10 # replace cadence R (ticks)

For the other algorithms you can just do:
/mapf algo cbs
/mapf algo single

Finally, you can use:
"/mapf start" to let the Agents run to their goal. If there is no possible path to 
their goal the agents might not move
You can also use "/mapf stop" to stop the Agents but I wouldn't recommend it
Once they have reached their goal or you want a reset just use good old:
/mapf reset

VERY IMPORTANT!
After "/mapf reset" you can just kill the existing villagers
Then recreate the arena. If you want to keep the same arena just go back to the centre
of the current one and use the same command but with an additional flag
/mapf arena Y (gridsize) keep
This will keep your existing arena. If you run the command without keep it will remove
everything and give you a fresh start.
Now you can repeat everything once again.

Your logs will be saved into a CSV inside run/save/"world_name"/mapf_logs