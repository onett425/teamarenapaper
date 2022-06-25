package me.toomuchzelda.teamarenapaper.teamarena.searchanddestroy;

import me.toomuchzelda.teamarenapaper.Main;
import me.toomuchzelda.teamarenapaper.teamarena.*;
import me.toomuchzelda.teamarenapaper.utils.BlockUtils;
import me.toomuchzelda.teamarenapaper.utils.ItemUtils;
import me.toomuchzelda.teamarenapaper.utils.TextUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.util.*;

public class SearchAndDestroy extends TeamArena
{
	//record it here from the map config but won't use it for anything
	protected boolean randomBases = false;
	//initialised in parseConfig
	protected Map<TeamArenaTeam, List<Bomb>> teamBombs;
	protected Map<BlockVector, Bomb> bombPositions;
	protected final ItemStack BASE_FUSE;
	public static final Component FUSE_NAME = ItemUtils.noItalics(Component.text("Bomb Fuse", NamedTextColor.GOLD));
	public static final List<Component> FUSE_LORE;

	static {
		FUSE_LORE = new ArrayList<>(2);
		FUSE_LORE.add(ItemUtils.noItalics(Component.text("Hold right click on a team's bomb to arm it", TextUtils.RIGHT_CLICK_TO)));
		FUSE_LORE.add(ItemUtils.noItalics(Component.text("Hold right click on your own bomb to disarm it", TextUtils.RIGHT_CLICK_TO)));
	}

	public SearchAndDestroy() {
		super();

		for(List<Bomb> bombsList : teamBombs.values()) {
			for(Bomb bomb : bombsList) {
				bomb.init();
			}
		}

		this.BASE_FUSE = new ItemStack(Material.BLAZE_POWDER);
		ItemMeta meta = BASE_FUSE.getItemMeta();
		meta.displayName(FUSE_NAME);
		meta.lore(FUSE_LORE);
		BASE_FUSE.setItemMeta(meta);
	}

	public void liveTick() {

		for(Bomb bomb : bombPositions.values()) {
			bomb.tick();
		}


		super.liveTick();
	}

	public void onInteract(PlayerInteractEvent event) {
		super.onInteract(event);
		if(event.useItemInHand() != Event.Result.DENY) {
			if(gameState == GameState.LIVE && event.getMaterial() == BASE_FUSE.getType() &&
					event.getAction().isRightClick() &&
					event.getClickedBlock() != null) {

				Block block = event.getClickedBlock();
				final int spamPeriods = 4 * 20;
				if(block.getType() == Material.TNT) {
					// check if team tnt or enemy tnt
					BlockVector blockLocation = block.getLocation().toVector().toBlockVector();
					Bomb clickedBomb = bombPositions.get(blockLocation);
					if(clickedBomb != null) {
						PlayerInfo pinfo = Main.getPlayerInfo(event.getPlayer());
						if(clickedBomb.getTeam() == pinfo.team) {
							if(clickedBomb.isArmed()) {
								//todo: disarming bomb
								clickedBomb.addClicker(pinfo.team, event.getPlayer(), getGameTick(), Bomb.getArmProgressPerTick(event.getItem()));
							}
							else {
								final String key = "sndDisarmOwnBomb";
								if(pinfo.messageHasCooldowned(key, spamPeriods)) {
									event.getPlayer().sendMessage(Component.text("You cannot arm your own bomb!", TextUtils.ERROR_RED));
								}
							}
						}
						else {
							if(clickedBomb.isArmed()) {
								final String key = "sndDisarmEnemyBomb";
								if(pinfo.messageHasCooldowned(key, spamPeriods)) {
									event.getPlayer().sendMessage(Component.text("You cannot disarm an enemy's bomb!", TextUtils.ERROR_RED));
								}
							}
							else {
								clickedBomb.addClicker(pinfo.team, event.getPlayer(), getGameTick(), Bomb.getArmProgressPerTick(event.getItem()));
							}
						}
					}
					else {
						Bukkit.broadcastMessage("Not a BOMB tnt.");
					}
				}
				else {
					final String key = "sndSpamFuseAirCooldown";
					if(Main.getPlayerInfo(event.getPlayer()).messageHasCooldowned(key, spamPeriods)) {
						event.getPlayer().sendMessage(Component.text("Use this on a bomb to arm or disarm it!", TextUtils.ERROR_RED));
					}
				}
			}
		}
	}

	@Override
	protected void givePlayerItems(Player player, PlayerInfo pinfo, boolean clear) {
		//need to clear and give the fuse first to put it in 1st slot
		player.getInventory().clear();
		player.getInventory().addItem(BASE_FUSE);
		super.givePlayerItems(player, pinfo, false);
	}

	@Override
	public void parseConfig(Map<String, Object> map) {
		super.parseConfig(map);

		Map<String, Object> customFlags = (Map<String, Object>) map.get("Custom");

		Main.logger().info("Custom Info: ");
		Main.logger().info(customFlags.toString());

		for (Map.Entry<String, Object> entry : customFlags.entrySet()) {
			this.teamBombs = new HashMap<>(customFlags.size());
			this.bombPositions = new HashMap<>();

			if (entry.getKey().equalsIgnoreCase("Random Base")) {
				try {
					randomBases = (boolean) entry.getValue();
				} catch (NullPointerException | ClassCastException e) {
					//do nothing
				}
			}
			else {
				TeamArenaTeam team = getTeamByRWFConfig(entry.getKey());
				if (team == null) {
					throw new IllegalArgumentException("Unknown team " + entry.getKey() + " Use BLUE or RED etc.(proper support coming later)");
				}

				List<String> configBombs = (List<String>) entry.getValue();
				List<Bomb> bombs = new ArrayList<>(configBombs.size());
				for(String bombCoords : configBombs) {
					BlockVector blockVector = BlockUtils.parseCoordsToVec(bombCoords, 0, 0, 0).toBlockVector();
					Bomb bomb = new Bomb(team, blockVector.toLocation(this.gameWorld));
					bombs.add(bomb);

					if(bombPositions.put(blockVector, bomb) != null) {
						throw new IllegalArgumentException("Two bombs are in the same position! Check the map's config.yml");
					}
				}

				teamBombs.put(team, bombs);
			}
		}
	}

	/**
	 * For compatibility with RWF 2 snd map config.yml
	 */
	protected TeamArenaTeam getTeamByRWFConfig(String name) {
		int spaceInd = name.indexOf(' ');
		name = name.substring(0, spaceInd);
		for(TeamArenaTeam team : teams) {
			if(team.getSimpleName().toLowerCase().replace(' ', '_').equals(name.toLowerCase())) {
				return team;
			}
		}

		return null;
	}

	@Override
	public void updateSidebar(Player player, SidebarManager sidebar) {
		//TODO
	}

	@Override
	public boolean canSelectKitNow() {
		return this.gameState.isPreGame();
	}

	@Override
	public boolean canSelectTeamNow() {
		return this.gameState == GameState.PREGAME;
	}

	@Override
	public boolean isRespawningGame() {
		return false;
	}

	@Override
	public File getMapPath() {
		return new File(super.getMapPath(), "SND");
	}
}
