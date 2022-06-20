package me.toomuchzelda.teamarenapaper;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.event.entity.EntityDamageItemEvent;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import me.toomuchzelda.teamarenapaper.teamarena.GameState;
import me.toomuchzelda.teamarenapaper.teamarena.GameType;
import me.toomuchzelda.teamarenapaper.teamarena.PlayerInfo;
import me.toomuchzelda.teamarenapaper.teamarena.TeamArena;
import me.toomuchzelda.teamarenapaper.teamarena.capturetheflag.CaptureTheFlag;
import me.toomuchzelda.teamarenapaper.teamarena.commands.CustomCommand;
import me.toomuchzelda.teamarenapaper.teamarena.damage.ArrowPierceManager;
import me.toomuchzelda.teamarenapaper.teamarena.damage.DamageEvent;
import me.toomuchzelda.teamarenapaper.teamarena.damage.DamageType;
import me.toomuchzelda.teamarenapaper.teamarena.kits.*;
import me.toomuchzelda.teamarenapaper.teamarena.kits.abilities.Ability;
import me.toomuchzelda.teamarenapaper.teamarena.preferences.Preference;
import me.toomuchzelda.teamarenapaper.teamarena.preferences.PreferenceManager;
import me.toomuchzelda.teamarenapaper.teamarena.preferences.Preferences;
import me.toomuchzelda.teamarenapaper.utils.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorldBorder;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import static me.toomuchzelda.teamarenapaper.teamarena.GameState.DEAD;
import static me.toomuchzelda.teamarenapaper.teamarena.GameState.LIVE;

public class EventListeners implements Listener
{

	public static final EnumMap<Material, Boolean> BREAKABLE_BLOCKS;

	static {
		BREAKABLE_BLOCKS = new EnumMap<Material, Boolean>(Material.class);

		for(Material mat : Material.values()) {
			if(mat.isBlock() && !mat.isCollidable() && !mat.name().endsWith("SIGN") && !mat.name().endsWith("TORCH")) {
				BREAKABLE_BLOCKS.put(mat, true);
			}
		}
	}

	public EventListeners(Plugin plugin) {
		Bukkit.getServer().getPluginManager().registerEvents(this,plugin);
	}

	//run the TeamArena tick
	//paper good spigot bad
	@EventHandler
	public void endTick(ServerTickEndEvent event) {
		PacketListeners.cancelDamageSounds = false;

		//run this before the game tick so there is a whole tick after prepDead and construction of the next
		// TeamArena instance
		if(Main.getGame().getGameState() == DEAD) {
			//use this opportunity to cleanup
			Iterator<Map.Entry<Player, PlayerInfo>> pinfoIter = Main.getPlayersIter();
			while(pinfoIter.hasNext()) {
				Entry<Player, PlayerInfo> entry = pinfoIter.next();

				if(!entry.getKey().isOnline()) {
					pinfoIter.remove();
				}
				else {
					PlayerInfo pinfo = entry.getValue();
					pinfo.clearMessageCooldowns();
				}
			}
			Main.playerIdLookup.entrySet().removeIf(idLookupEntry -> !idLookupEntry.getValue().isOnline());

			// initialize next game
			if (TeamArena.nextGameType == null) {
				TeamArena.nextGameType = GameType.values()[MathUtils.random.nextInt(GameType.values().length)];
			}

			try {
				Constructor<? extends TeamArena> constructor = TeamArena.nextGameType.gameClazz.getConstructor();
				TeamArena game = constructor.newInstance();
				Main.setGame(game);
				TeamArena.nextGameType = null;
			} catch (ReflectiveOperationException ex) {
				throw new Error("Failed to create game reflectively", ex);
			}
		}

		try {
			Main.getGame().tick();
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		/*try {
			//update nametag positions
			for (PlayerInfo pinfo : Main.getPlayerInfos()) {
				if (pinfo.nametag != null) {
					pinfo.nametag.updatePosition();
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}*/

		//every 3 minutes
		int count = event.getTickNumber() % (3 * 60  *20);
		if(count == 0) {
			ArrowPierceManager.cleanup();
		}
		else if(count == 10) {
			for(PlayerInfo pinfo : Main.getPlayerInfos()) {
				pinfo.getMetadataViewer().cleanUp();
			}
		}

		PacketListeners.cancelDamageSounds = true;
	}

	private final HashMap<UUID, CompletableFuture<Map<Preference<?>, ?>>> preferenceFutureMap = new HashMap<>();
	@EventHandler(priority = EventPriority.MONITOR)
	public void asynchronousPlayerPreLoginEventHandler(AsyncPlayerPreLoginEvent e) {
		if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED)
			return;
		synchronized (preferenceFutureMap) {
			preferenceFutureMap.put(e.getUniqueId(), PreferenceManager.fetchPreferences(e.getUniqueId()));
		}
	}


	//these three events are called in this order
	@EventHandler
	public void playerLogin(PlayerLoginEvent event) {
		UUID uuid = event.getPlayer().getUniqueId();
		//todo: read from MySQL server or something for stored player data.
		// or use persistent data containers, or option to use either
		// and also use the PreLoginEvent
		PlayerInfo playerInfo;

		//todo: read perms from db or other
		String playerName = event.getPlayer().getName();
		if ("toomuchzelda".equalsIgnoreCase(playerName) || "jacky8399".equalsIgnoreCase(playerName) || "Onett_".equalsIgnoreCase(playerName)) {
			event.getPlayer().setOp(true); // lol
			playerInfo = new PlayerInfo(CustomCommand.PermissionLevel.OWNER, event.getPlayer());
		} else {
			playerInfo = new PlayerInfo(CustomCommand.PermissionLevel.ALL, event.getPlayer());
		}

		synchronized (preferenceFutureMap) {
			CompletableFuture<Map<Preference<?>, ?>> future = preferenceFutureMap.remove(uuid);
			if (future == null) {
				event.disallow(Result.KICK_OTHER, Component.text("Failed to load preferences!")
						.color(NamedTextColor.DARK_RED));
				return;
			}
			playerInfo.setPreferenceValues(future.join());
		}

		Main.addPlayerInfo(event.getPlayer(), playerInfo);
		Main.getGame().loggingInPlayer(event.getPlayer(), playerInfo);
		Main.playerIdLookup.put(event.getPlayer().getEntityId(), event.getPlayer());
	}

	@EventHandler
	public void playerSpawn(PlayerSpawnLocationEvent event) {
		event.setSpawnLocation(Main.getPlayerInfo(event.getPlayer()).spawnPoint);
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent event) {
		//disable yellow "Player has joined the game" messages
		event.joinMessage(null);
		//event.getPlayer().setScoreboard(SidebarManager.SCOREBOARD);
		Main.getPlayerInfo(event.getPlayer()).getScoreboard().set();
		//new Hologram(event.getPlayer());
		Main.getGame().joiningPlayer(event.getPlayer());
	}

	//don't show any commands the player doesn't have permission to use in the tab list
	@EventHandler
	public void playerCommandSend(PlayerCommandSendEvent event) {
		@NotNull Collection<String> commands = event.getCommands();

		Player player = event.getPlayer();
		PlayerInfo pinfo = Main.getPlayerInfo(player);

		commands.removeIf(commandStr -> {
			Command command = Bukkit.getCommandMap().getCommand(commandStr);
			if (command == null) // command not found
				return true;
			if (command instanceof CustomCommand customCommand) {
				return pinfo.permissionLevel.compareTo(customCommand.permissionLevel) < 0;
			} else if (command.getPermission() != null) {
				return !player.hasPermission(command.getPermission());
			}
			return false;
		});
	}

	//public static int i = 0;

	//handle tab-completes asynchronously
	/*@EventHandler
	public void asyncTabComplete(AsyncTabCompleteEvent event) {
		//parse if it's a command first

		Bukkit.broadcastMessage(event.getBuffer());
		Bukkit.broadcastMessage("----------" + i++);
		/*for(AsyncTabCompleteEvent.Completion completion : event.completions()) {
			Bukkit.broadcast(Component.text(completion.suggestion() + " + ").append(completion.tooltip() == null ? Component.empty() : completion.tooltip()));
		}
		Bukkit.broadcastMessage("============");

		String typed = event.getBuffer();
		if(typed.startsWith("/")) {
			int firstSpaceIdx = typed.indexOf(' ');
			String commandName = typed.substring(1, firstSpaceIdx);
			CustomCommand typedCommand = CustomCommand.getFromName(commandName);
			//if it's null it may be a vanilla or other plugin command so just let the normal process happen for that
			if(typedCommand != null) {
				event.setHandled(true);
				String argsString = typed.substring(firstSpaceIdx + 1); //get all the arguments
				String[] args = argsString.split(" ");
				// if the typed args ends with " " we need to manually add the space to the args array
				if(argsString.endsWith(" ")) {
					args = Arrays.copyOf(args, args.length + 1);
					args[args.length - 1] = " ";
				}
				List<String> stringSuggestions = typedCommand.tabComplete(event.getSender(), commandName, args);
				LinkedList<AsyncTabCompleteEvent.Completion> completionSuggestions = new LinkedList<>();
				for(final String s : stringSuggestions) {
					AsyncTabCompleteEvent.Completion completion = AsyncTabCompleteEvent.Completion.completion(s);
					completionSuggestions.add(completion);
				}

				event.completions(completionSuggestions);
			}
		}
	}*/

	@EventHandler
	public void playerQuit(PlayerQuitEvent event) {
		event.quitMessage(null);
		Main.getGame().leavingPlayer(event.getPlayer());
		//Main.getPlayerInfo(event.getPlayer()).nametag.remove();
		Main.removePlayerInfo(event.getPlayer());
		Main.playerIdLookup.remove(event.getPlayer().getEntityId());
	}

	@EventHandler
	public void playerMove(PlayerMoveEvent event) {
		TeamArena game = Main.getGame();
		if(game.getGameState() == GameState.GAME_STARTING && !game.isSpectator(event.getPlayer())) {
			Location prev = event.getFrom();
			Location next = event.getTo();

			if(prev.getX() != next.getX() || prev.getZ() != next.getZ()) {
				event.setCancelled(true);
			}
		}

		//prevent them from moving outside the game border
		if (event.isCancelled())
			return;

		Vector from = event.getFrom().toVector();
		Vector to = event.getTo().toVector();
		// dynamic world border
		if (from.distanceSquared(to) != 0) {
			Player player = event.getPlayer();
			BoundingBox border = game.getBorder();
			// only display when distance to border <= 10 blocks
			if (MathUtils.distanceBetween(border, from) <= 10) {
				Vector closest = new Vector(
						from.getX() > border.getCenterX() ? border.getMaxX() : border.getMinX(),
						0,
						from.getZ() > border.getCenterZ() ? border.getMaxZ() : border.getMinZ()
				);

				WorldBorder worldBorder = player.getWorldBorder();
				// calculate the center of the world border
				// such that at size 512 the world border edge would line up with the closest point
				double centerX = closest.getX() + Math.signum(from.getX() - closest.getX()) * 256,
						centerZ = closest.getZ() + Math.signum(from.getZ() - closest.getZ()) * 256;
				if (worldBorder == null || // no border
						((CraftWorldBorder) worldBorder).getHandle().getLerpTarget() == 768 || // fading away
						Math.abs(worldBorder.getCenter().getX() - centerX) > Vector.getEpsilon() ||
						Math.abs(worldBorder.getCenter().getZ() - centerZ) > Vector.getEpsilon()) {
					// wrong center, replace border
					if (worldBorder == null) {
						worldBorder = Bukkit.createWorldBorder();
						worldBorder.setCenter(centerX, centerZ);
						worldBorder.setSize(768);
						worldBorder.setSize(512, 1);
						worldBorder.setWarningDistance(3);
						worldBorder.setWarningTime(0);
						player.setWorldBorder(worldBorder);
					} else {
						worldBorder.setCenter(centerX, centerZ);
						worldBorder.setSize(512, 1);
					}
				}
			} else {
				WorldBorder worldBorder = player.getWorldBorder();
				if (worldBorder != null && ((CraftWorldBorder) worldBorder).getHandle().getLerpRemainingTime() == 0) {
					worldBorder.setSize(768, 2);
					Bukkit.getScheduler().runTaskLater(Main.getPlugin(), () -> {
						// check if the world border is the same and has faded out completely
						if (player.getWorldBorder() == worldBorder && worldBorder.getSize() == 768) {
							player.setWorldBorder(null);
						}
					}, 40);
				}
			}
		}


		if(!game.getBorder().contains(to)) {
			event.setCancelled(true);
			//if they're hitting the bottom border call it falling into the void
			if(to.getY() < game.getBorder().getMinY()) {
				if(game.getGameState() == LIVE) {
					event.setCancelled(false);
					DamageEvent dEvent = DamageEvent.newDamageEvent(event.getPlayer(), 9999d, DamageType.VOID, null, false);
					Main.getGame().queueDamage(dEvent);
				}
			}
		}
	}

	@EventHandler
	public void blockBreak(BlockBreakEvent event) {
		if((Main.getGame() != null && Main.getGame().getGameState() != LIVE &&
				!BREAKABLE_BLOCKS.containsKey(event.getBlock().getType())) || event.getPlayer().getGameMode() != GameMode.CREATIVE) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void blockPlace(BlockPlaceEvent event) {
		if(event.getPlayer().getGameMode() != GameMode.CREATIVE) {
			event.setCancelled(true);
		}

		Main.getGame().onPlaceBlock(event);

		for(Ability ability : Kit.getAbilities(event.getPlayer())) {
			ability.onPlaceBlock(event);
		}
	}

	/**
	 * prevent explosions from breaking blocks
	 */
	@EventHandler
	public void entityExplode(EntityExplodeEvent event) {
		event.blockList().clear();
	}

	@EventHandler
	public void blockExplode(BlockExplodeEvent event) {
		event.blockList().clear();
	}

	@EventHandler
	public void inventoryCreative(InventoryCreativeEvent event) {
		if(Main.getGame() != null && event.getWhoClicked() instanceof Player p) {
			Ability[] abilities = Kit.getAbilities(p);
			for(Ability a : abilities) {
				if(a instanceof KitReach.ReachAbility) {
					event.setCancelled(true);
					return;
				}
			}
		}
	}

	/*@EventHandler
	public void entityPoseChange(EntityPoseChangeEvent event) {
		if(event.getEntity() instanceof  Player p) {
			Hologram hologram = Main.getPlayerInfo(p).nametag;
			if(hologram != null) {
				//need to update the position of the nametag when Pose changes
				// would do this within the event but we need to let the event pass to be able to get the
				// player's changed height (otherwise have to use hardcoded values)
				//just signal to update it at the end of the tick
				hologram.poseChanged = true;
			}
		}
	}*/

	//create and cache damage events
	@EventHandler
	public void entityDamage(EntityDamageEvent event) {
		DamageEvent.handleBukkitEvent(event);
	}

	@EventHandler
	public void playerDeath(PlayerDeathEvent event) {
		Main.logger().warning("PlayerDeathEvent called! not good");
		Thread.dumpStack();
		event.setCancelled(true);
	}

	//shouldn't run since we handle deaths on our own, but just in case
	// try have the server avoid loading the default "world" world
	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event) {
		event.setRespawnLocation(Main.getPlayerInfo(event.getPlayer()).spawnPoint);
	}

	//stop projectiles from inheriting thrower's velocity
	// like moving too up/down when player is jumping/falling/rising
	//this event is fired for shooting bows including by players
	@EventHandler
	public void entityShootBow(EntityShootBowEvent event) {
		event.getProjectile().setVelocity(EntityUtils.projectileLaunchVector(event.getEntity(),
				event.getProjectile().getVelocity(), EntityUtils.VANILLA_PROJECTILE_SPRAY));

		if(event.getProjectile() instanceof AbstractArrow aa)
			aa.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);

		if(event.getEntity() instanceof Player p) {
			Ability[] abilities = Kit.getAbilities(p);
			for(Ability a : abilities) {
				a.onShootBow(event);
			}
		}
	}

	@EventHandler
	public void entityLoadCrossbow(EntityLoadCrossbowEvent event) {
		if(Main.getGame().getGameState() == LIVE) {
			if (event.getEntity() instanceof Player p) {
				Ability[] abilities = Kit.getAbilities(p);
				for (Ability a : abilities) {
					a.onLoadCrossbow(event);
				}
			}
		}
	}

	//^^
	//this event fired by players throwing projectiles (not bows!!)
	@EventHandler
	public void playerLaunchProjectile(PlayerLaunchProjectileEvent event) {
		/*Bukkit.broadcastMessage(event.getItemStack().getType().toString());
		Bukkit.broadcastMessage(event.getProjectile().getVelocity().toString());*/

		event.getProjectile().setVelocity(EntityUtils.projectileLaunchVector(event.getPlayer(),
				event.getProjectile().getVelocity(), EntityUtils.VANILLA_PROJECTILE_SPRAY));

		if(Main.getGame() != null) {
			Player p = event.getPlayer();
			if(Main.getGame().getGameState() == LIVE) {
				if (Main.getGame() instanceof CaptureTheFlag ctf && ctf.isFlagCarrier(p)) {
					event.setCancelled(true);
					PlayerInfo pinfo = Main.getPlayerInfo(p);
					if (pinfo.getPreference(Preferences.RECEIVE_GAME_TITLES)) {
						PlayerUtils.sendTitle(p, Component.empty(), CaptureTheFlag.CANT_TELEPORT_HOLDING_FLAG_TITLE, 10, 25, 10);
					}
					p.sendMessage(CaptureTheFlag.CANT_TELEPORT_HOLDING_FLAG_MESSAGE);
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.AMBIENT, 2, 0.5f);
				}

				if(!event.isCancelled()) {
					Ability[] abilites = Kit.getAbilities(p);
					for (Ability a : abilites) {
						a.onLaunchProjectile(event);
					}
				}
			}
		}
	}

	@EventHandler
	public void playerTeleport(PlayerTeleportEvent event) {
		PlayerTeleportEvent.TeleportCause cause = event.getCause();
		if(cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
			cause == PlayerTeleportEvent.TeleportCause.PLUGIN ||
			cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) //unkown is often rubber-bands and lagbacks
			return;

		if(Main.getGame() != null) {

			if(cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ||
				cause == PlayerTeleportEvent.TeleportCause.END_PORTAL ||
				cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
				event.setCancelled(true);
			}
			else if(!Main.getGame().getBorder().contains(event.getTo().toVector())) {
				event.setCancelled(true);

				if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
					event.getPlayer().sendMessage(Component.text("One of your ender pearls landed outside the border. " +
							"Aim better!").color(TextUtils.ERROR_RED));
				}
				else if(cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
					event.getPlayer().sendMessage(Component.text("This fruit tried to take you outside the border, " +
									"so now you just go nowhere because I have deemed finding a safe alternative position" +
									" to be too much trouble (i am lazy). Here's a free diamond! - toomuchzelda")
							.color(TextUtils.ERROR_RED));
					event.getPlayer().getInventory().addItem(new ItemStack(Material.DIAMOND));
				}
			}

			if(Main.getGame() instanceof CaptureTheFlag ctf && ctf.isFlagCarrier(event.getPlayer())) {
				Player p = event.getPlayer();
				event.setCancelled(true);
				PlayerInfo pinfo = Main.getPlayerInfo(p);
				if(pinfo.getPreference(Preferences.RECEIVE_GAME_TITLES)) {
					PlayerUtils.sendTitle(p, Component.empty(), CaptureTheFlag.CANT_TELEPORT_HOLDING_FLAG_TITLE, 10, 25, 10);
				}
				p.sendMessage(CaptureTheFlag.CANT_TELEPORT_HOLDING_FLAG_MESSAGE);
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.AMBIENT, 2, 0.5f);
			}
		}
	}


	//stop projectiles from colliding with spectators
	// and the flags in CTF
	@EventHandler
	public void projectileCollide(ProjectileCollideEvent event) {
		if(Main.getGame() != null) {
			if(event.getCollidedWith() instanceof Player p && Main.getGame().isSpectator(p))
				event.setCancelled(true);
			else if(event.getCollidedWith() instanceof ArmorStand stand && Main.getGame() instanceof CaptureTheFlag ctf
					&& ctf.flagStands.containsKey(stand))
				event.setCancelled(true);

			if(!event.isCancelled() && event.getEntity().getShooter() instanceof Player p) {
				for(Ability a : Kit.getAbilities(p)) {
					a.projectileHitEntity(event);
				}
			}
		}
	}

	@EventHandler
	public void projectileHit(ProjectileHitEvent event) {
		if(Main.getGame() != null) {
			if(event.getHitBlock() != null && event.getEntity().getShooter() instanceof Player p) {
				Ability[] abilities = Kit.getAbilities(p);
				for(Ability a : abilities) {
					if(a instanceof KitPyro.PyroAbility pyroAbility) {
						pyroAbility.onProjectileHit(event);
						return;
					}
				}
			}
		}
	}

	//stop hunger
	@EventHandler
	public void entityExhaustion(EntityExhaustionEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void entityRegainHealth(EntityRegainHealthEvent event) {
		if(event.getEntity() instanceof LivingEntity living) {
			if(KitVenom.POISONED_ENTITIES.containsKey(living)){
				event.setCancelled(true);
			}

			if(!event.isCancelled() && event.getEntity() instanceof Player p) {
				Main.getPlayerInfo(p).getKillAssistTracker().heal(event.getAmount());
			}
		}
	}

	@EventHandler
	public void arrowBodyCountChange(ArrowBodyCountChangeEvent event) {
		//not worth adding a new method to Ability.java for this one
		if(Main.getGame() != null && Main.getGame().getGameState() == LIVE) {
			if (event.getEntity() instanceof Player p) {
				Ability[] abilities = Kit.getAbilities(p);
				for(int i = 0; i < abilities.length; i++) {
					if(abilities[i] instanceof KitGhost.GhostAbility ghosta) {
						ghosta.arrowCountDecrease(event);
						return;
					}
				}
			}
		}
	}

	@EventHandler
	public void playerItemCooldown(PlayerItemCooldownEvent event) {
		if(Main.getGame() != null && Main.getGame().getGameState() == LIVE) {
			Ability[] abilities = Kit.getAbilities(event.getPlayer());
			for(Ability a : abilities) {
				a.onItemCooldown(event);
			}
		}
	}

	//stop item damage/breaking
	@EventHandler
	public void entityDamageItem(EntityDamageItemEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void playerItemDamage(PlayerItemDamageEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void playerDropItem(PlayerDropItemEvent event) {
		if(event.getPlayer().getGameMode() != GameMode.CREATIVE) {
			event.setCancelled(true);
		}

		if(Main.getGame() != null && Main.getGame().getGameState() == LIVE) {
			Ability[] abilities = Kit.getAbilities(event.getPlayer());
			for (Ability a : abilities) {
				a.playerDropItem(event);
			}
		}
	}

	//stop items being moved from one inventory to another (chests etc)
	@EventHandler
	public void inventoryMoveItem(InventoryMoveItemEvent event) {
		event.setCancelled(true);
	}

	//stop players from messing with the armor of CTF Flags
	@EventHandler
	public void playerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
		if(Main.getGame() instanceof CaptureTheFlag ctf && ctf.flagStands.containsKey(event.getRightClicked())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerArmorChange(InventoryClickEvent e) {
		TeamArena game = Main.getGame();
		InventoryAction action = e.getAction();
		// these two actions move the current item so check the current item
		if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.HOTBAR_SWAP) {
			if (!game.isWearableArmorPiece(e.getCurrentItem()))
				e.setCancelled(true);
			else if (e.getHotbarButton() != -1 && !game.isWearableArmorPiece(e.getView().getBottomInventory().getItem(e.getHotbarButton())))
				e.setCancelled(true);
		} else if (e.getSlotType() == InventoryType.SlotType.ARMOR) {
			if (!game.isWearableArmorPiece(e.getCursor())) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerArmorDrag(InventoryDragEvent e) {
		ItemStack draggedItem = e.getOldCursor();

		boolean isDraggingOnArmorSlot = false;
		if(e.getInventory().getHolder() instanceof HumanEntity) {
			for(int i : e.getInventorySlots()) {
				if(ItemUtils.isArmorSlotIndex(i)) {
					isDraggingOnArmorSlot = true;
					break;
				}
			}
		}

		if(isDraggingOnArmorSlot && !Main.getGame().isWearableArmorPiece(draggedItem)) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void playerInteract(PlayerInteractEvent event) {
		if(Main.getGame() != null) {
			Main.getGame().onInteract(event);

			if(Main.getGame().getGameState() == LIVE) {
				Ability[] abilities = Kit.getAbilities(event.getPlayer());
				for (Ability a : abilities) {
					a.onInteract(event);
				}
			}
		}
	}

	@EventHandler
	public void playerInteractEntity(PlayerInteractEntityEvent event) {
		if(Main.getGame() != null) {
			Main.getGame().onInteractEntity(event);

			if(Main.getGame().getGameState() == LIVE) {
				Ability[] abilities = Kit.getAbilities(event.getPlayer());
				for (Ability a : abilities) {
					a.onInteractEntity(event);
				}
			}
		}
	}

	@EventHandler
	public void onRecipeUnlock(PlayerRecipeDiscoverEvent e) {
		e.setCancelled(true);
	}

	@EventHandler
	public void onEat(PlayerItemConsumeEvent event){
		Player player = event.getPlayer();
		if(KitVenom.POISONED_ENTITIES.containsKey((LivingEntity)player)){
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void playerAdvancementCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
		event.setCancelled(true);
	}


	static final Component MOTD_SEPARATOR = Component.textOfChildren(Component.space(),
			Component.text("|", NamedTextColor.DARK_RED, TextDecoration.BOLD), Component.space());
	static final Component MOTD = Component.textOfChildren(
			Component.text("             "),
			TextUtils.getUselessRGBText("Blue Warfare", TextColor.color(0x060894), TextColor.color(0x1ad3f0)),
			Component.space(),
			TextUtils.getUselessRainbowText("[1.18.2]"),
			Component.newline(),
			TextUtils.getUselessRGBText("King of the Hill", TextColor.color(0x595959), TextColor.color(0xadadad)),
			MOTD_SEPARATOR,
			TextUtils.getUselessRGBText("Capture the Flag", TextColor.color(0x1d6e16), TextColor.color(0x00ff40)),
			MOTD_SEPARATOR,
			TextUtils.getUselessRGBText("UnbalancedBS", TextColor.color(0x631773), TextColor.color(0xff00f2))
	);
	@EventHandler
	public void onMOTD(PaperServerListPingEvent e) {
		e.motd(MOTD);
	}
}