package me.toomuchzelda.teamarenapaper.teamarena.kits.demolitions;

import me.toomuchzelda.teamarenapaper.Main;
import me.toomuchzelda.teamarenapaper.scoreboard.PlayerScoreboard;
import me.toomuchzelda.teamarenapaper.teamarena.SidebarManager;
import me.toomuchzelda.teamarenapaper.teamarena.TeamArena;
import me.toomuchzelda.teamarenapaper.teamarena.TeamArenaTeam;
import me.toomuchzelda.teamarenapaper.utils.BlockUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.EulerAngle;

import java.util.HashMap;

public abstract class DemoMine
{
	public static final EulerAngle LEG_ANGLE = new EulerAngle(1.5708d, 0 ,0); //angle for legs so boots r horizontal
	public static final int TNT_TIME_TO_DETONATE = 20;
	public static final int TIME_TO_ARM = 30;
	
	//used to set the colour of the glowing effect on the mine armor stand's armor
	// actual game teams don't matter, just need for the colour
	private static final HashMap<NamedTextColor, Team> GLOWING_COLOUR_TEAMS = new HashMap<>(16);
	static final HashMap<Integer, DemoMine> ARMOR_STAND_ID_TO_DEMO_MINE = new HashMap<>(20, 0.4f);
	
	static {
		for(NamedTextColor color : NamedTextColor.NAMES.values()) {
			Team bukkitTeam = SidebarManager.SCOREBOARD.registerNewTeam("DemoMine" + color.value());
			bukkitTeam.color(color);
			bukkitTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
			
			GLOWING_COLOUR_TEAMS.put(color, bukkitTeam);
			PlayerScoreboard.addGlobalTeam(bukkitTeam);
		}
	}
	
	final Player owner;
	public final TeamArenaTeam team;
	final Team glowingTeam;
	ArmorStand[] stands;
	final Axolotl axolotl; //the mine's interactable hitbox
	Player triggerer; //store the player that stepped on it for shaming OR the demo if remote detonate
	
	//for construction
	final Location baseLoc;
	final Color color;
	EquipmentSlot armorSlot;
	
	int damage = 0; //amount of damage it has
	//whether to remove on next tick
	// whether it needs to be removed from hashmaps is checked every tick, and we can't remove it on the same tick
	// as the damage events are processed after the ability tick, so we need to 'schedule' it for removal next tick
	boolean removeNextTick = false;
	int creationTime; //store for knowing when it gets 'armed' after placing
	
	MineType type;
	
	public DemoMine(Player demo, Block block) {
		owner = demo;
		this.team = Main.getPlayerInfo(owner).team;
		this.creationTime = TeamArena.getGameTick();
		
		this.color = BlockUtils.getBlockBukkitColor(block);
		
		this.glowingTeam = GLOWING_COLOUR_TEAMS.get((NamedTextColor) team.getPaperTeam().color());
		
		World world = block.getWorld();
		double topOfBlock = BlockUtils.getBlockHeight(block);
		//put downwards slightly so rotated legs lay flat on ground and boots partially in ground
		Location blockLoc = block.getLocation();
		this.baseLoc = blockLoc.add(0.5d, topOfBlock - 0.85d, 0.5d);
		
		this.axolotl = (Axolotl) world.spawnEntity(baseLoc.clone().add(0, 0.65, 0), EntityType.AXOLOTL);
		axolotl.setAI(false);
		axolotl.setSilent(true);
		axolotl.setInvisible(true);
	}
	
	void removeEntites() {
		//glowingTeam.removeEntities(stands);
		PlayerScoreboard.removeMembersAll(glowingTeam, stands);
		for (ArmorStand stand : stands) {
			ARMOR_STAND_ID_TO_DEMO_MINE.remove(stand.getEntityId());
			stand.remove();
		}
		axolotl.remove();
	}
	
	/**
	 * @return return true if mine extinguised/removed
	 */
	boolean hurt() {
		this.damage++;
		World world = this.axolotl.getWorld();
		for(int i = 0; i < 3; i++) {
			world.playSound(axolotl.getLocation(), Sound.BLOCK_GRASS_HIT, 999, 0.5f);
			world.spawnParticle(Particle.CLOUD, axolotl.getLocation().add(0d, 0.2d, 0d), 1,
					0.2d, 0.2d, 0.2d, 0.02d);
		}
		
		if(this.damage >= type.damageToKill) {
			// game command: /particle minecraft:cloud ~3 ~0.2 ~ 0.2 0.2 0.2 0.02 3 normal
			world.spawnParticle(Particle.CLOUD, axolotl.getLocation().add(0d, 0.2d, 0d), 3,
					0.2d, 0.2d, 0.2d, 0.02d);
			world.playSound(axolotl.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 1f);
			world.playSound(axolotl.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 1.3f);
			world.playSound(axolotl.getLocation(), Sound.BLOCK_STONE_BREAK, 1.5f, 1f);
			this.removeEntites();
			return true;
		}
		return false;
	}
	
	void trigger(Player triggerer) {
		this.triggerer = triggerer;
		World world = axolotl.getWorld();
		
		world.playSound(axolotl.getLocation(), Sound.ENTITY_CREEPER_HURT, 1f, 0f);
		world.playSound(axolotl.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0f);
		
		//subclass here
	}
	
	abstract boolean isDone();
	
	public static boolean isMineStand(int id) {
		return ARMOR_STAND_ID_TO_DEMO_MINE.containsKey(id);
	}
	
	public static DemoMine getStandMine(int id) {
		return ARMOR_STAND_ID_TO_DEMO_MINE.get(id);
	}
	
	public static ArmorStand getMineStand(int id) {
		DemoMine mine = getStandMine(id);
		if(mine != null) {
			ArmorStand[] stands = mine.stands;
			for (ArmorStand stand : stands) {
				if (stand.getEntityId() == id)
					return stand;
			}
		}
		
		return null;
	}
	
	static void clearMap() {
		ARMOR_STAND_ID_TO_DEMO_MINE.clear();
	}
	
	private static void clearTeams() {
		for(Team glowTeam : GLOWING_COLOUR_TEAMS.values()) {
			PlayerScoreboard.removeEntriesAll(glowTeam, glowTeam.getEntries());
			glowTeam.removeEntries(glowTeam.getEntries());
		}
	}
}
