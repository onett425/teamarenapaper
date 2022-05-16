package me.toomuchzelda.teamarenapaper.teamarena.kits;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import me.toomuchzelda.teamarenapaper.Main;
import me.toomuchzelda.teamarenapaper.teamarena.PlayerInfo;
import me.toomuchzelda.teamarenapaper.teamarena.TeamArenaTeam;
import me.toomuchzelda.teamarenapaper.teamarena.damage.DamageEvent;
import me.toomuchzelda.teamarenapaper.teamarena.damage.DamageType;
import me.toomuchzelda.teamarenapaper.teamarena.kits.abilities.Ability;
import me.toomuchzelda.teamarenapaper.teamarena.preferences.Preferences;
import me.toomuchzelda.teamarenapaper.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * @author onett425
 */
public class KitSniper extends Kit {
	public static final ItemStack GRENADE;
	public static final ItemStack SNIPER;

	public static final AttributeModifier KNIFE_SPEED = new AttributeModifier(
			UUID.fromString("743e8aec-10f7-44c7-b0b0-cf1f32634c72"),
			"Sniper Knife", 0.2, //20% = speed 1
			AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlot.HAND);

	static {
		GRENADE = new ItemStack(Material.TURTLE_HELMET);
		ItemMeta grenadeMeta = GRENADE.getItemMeta();
		grenadeMeta.displayName(ItemUtils.noItalics(Component.text("Frag Grenade")));
		GRENADE.setItemMeta(grenadeMeta);

		SNIPER = new ItemStack(Material.SPYGLASS);
		ItemMeta rifleMeta = SNIPER.getItemMeta();
		rifleMeta.displayName(ItemUtils.noItalics(Component.text("CheyTac Intervention")));
		SNIPER.setItemMeta(rifleMeta);
	}

	public KitSniper() {
		super("Sniper", "Be careful when sniping... Too much movement and your aim will worsen. Don't forget to throw the grenade if you pull the pin btw.", Material.SPYGLASS);

		setArmor(new ItemStack(Material.LEATHER_HELMET), new ItemStack(Material.LEATHER_CHESTPLATE),
				new ItemStack(Material.LEATHER_LEGGINGS), new ItemStack(Material.LEATHER_BOOTS));

		ItemStack sword = new ItemStack(Material.IRON_SWORD);
		ItemMeta meta = sword.getItemMeta();
		meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, KNIFE_SPEED);
		sword.setItemMeta(meta);

		setItems(sword, SNIPER, GRENADE);
		setAbilities(new SniperAbility());
	}

	public static class SniperAbility extends Ability {
		private final Set<UUID> RECEIVED_SNIPER_CHAT_MESSAGE = new HashSet<>();
		private final Set<UUID> RECEIVED_GRENADE_CHAT_MESSAGE = new HashSet<>();
		public static final TextColor SNIPER_MSG_COLOR = TextColor.color(89, 237, 76);
		public static final TextColor GRENADE_MSG_COLOR = TextColor.color(66, 245, 158);

		final List<BukkitTask> GRENADE_TASKS = new ArrayList<>();
		final Random gunInaccuracy = new Random();

		@Override
		public void unregisterAbility() {
			GRENADE_TASKS.forEach(BukkitTask::cancel);
			GRENADE_TASKS.clear();
		}

		@Override
		public void giveAbility(Player player) {
			player.setExp(0.999f);
		}

		@Override
		public void removeAbility(Player player) {
			player.setExp(0);
			player.getInventory().remove(Material.TURTLE_HELMET);
			var uuid = player.getUniqueId();
			RECEIVED_SNIPER_CHAT_MESSAGE.remove(uuid);
			RECEIVED_GRENADE_CHAT_MESSAGE.remove(uuid);
		}

		public void throwGrenade(Player player, double amp) {
			World world = player.getWorld();
			Location origin = player.getEyeLocation();
			Item grenade = world.dropItem(origin, new ItemStack(Material.TURTLE_HELMET), item -> {
				item.setCanMobPickup(false);
				item.setCanPlayerPickup(false);
				item.setUnlimitedLifetime(true);
				item.setVelocity(origin.getDirection().multiply(amp));
			});
			world.playSound(origin, Sound.ENTITY_CREEPER_PRIMED, 1f, 1.1f);

			// schedule task
			TeamArenaTeam team = Main.getPlayerInfo(player).team;
			Particle.DustOptions particleOptions = new Particle.DustOptions(team.getColour(), 2);
			BukkitTask runnable = new BukkitRunnable() {
				//Grenade explosion
				int timer = player.getCooldown(Material.TURTLE_HELMET);

				public void run() {
					//Grenade Particles when it is thrown
					//In Motion
					if (!grenade.isOnGround()) {
						world.spawnParticle(Particle.REDSTONE, grenade.getLocation(), 1, particleOptions);
					} else {
						//On the ground
						world.spawnParticle(Particle.REDSTONE, grenade.getLocation(),
								2, 2, 2,2, particleOptions);
					}
					if (timer <= 0) {
						world.createExplosion(grenade.getLocation(), 1.7f, false, false);
						player.getInventory().addItem(GRENADE);
						grenade.remove();
						cancel();
					}
					timer--;
				}
			}.runTaskTimer(Main.getPlugin(), 0, 0);
			GRENADE_TASKS.add(runnable);
		}

		@Override
		public void onInteract(PlayerInteractEvent event) {
			Material mat = event.getMaterial();
			ItemStack item = event.getItem();
			Player player = event.getPlayer();
			World world = player.getWorld();
			PlayerInventory inv = player.getInventory();
			Action action = event.getAction();
			PlayerInfo pinfo = Main.getPlayerInfo(player);

			//Grenade Pull Pin
			if (mat == Material.TURTLE_HELMET && !player.hasCooldown(Material.TURTLE_HELMET) && player.getExp() == 0.999f && player.getInventory().getItemInMainHand().getType() == Material.TURTLE_HELMET) {
				Component actionBar = Component.text("Left Click to THROW    Right Click to TOSS").color(TextColor.color(242, 44, 44));
				Component text = Component.text("Left Click to throw the grenade, Right Click to lightly toss it").color(TextColor.color(242, 44, 44));
				if (pinfo.getPreference(Preferences.KIT_ACTION_BAR)) {
					player.sendActionBar(actionBar);
				}
				if (pinfo.getPreference(Preferences.KIT_CHAT_MESSAGES)) {
					player.sendMessage(text);
				}
				player.setExp(0);
				player.setCooldown(Material.TURTLE_HELMET, (int) 3.5 * 20);
				world.playSound(player, Sound.ITEM_FLINTANDSTEEL_USE, 2.0f, 1.5f);
			}
			//Grenade Throw
			//Main Hand ONLY
			else if (mat == Material.TURTLE_HELMET && player.hasCooldown(Material.TURTLE_HELMET) && event.getHand() == EquipmentSlot.HAND) {
				//Removes 1 grenade from hand
				inv.setItemInMainHand(item.subtract());
				if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
					//Left Click => Hard Throw
					throwGrenade(player, 1.5d);
				} else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
					//Right Click => Soft Toss
					throwGrenade(player, 0.8d);
				}
			}
		}

		//Headshot
		@Override
		public void projectileHitEntity(ProjectileCollideEvent event) {
			Projectile projectile = event.getEntity();
			Entity victim = event.getCollidedWith();
			Player shooter = (Player) projectile.getShooter();
			if (victim instanceof Player player && projectile.getType() == EntityType.ARROW) {
				double headLocation = player.getLocation().getY();
				double projectileHitY = projectile.getLocation().getY();
				//Must consider when player is below the other player, which makes getting headshots much harder.
				double headshotThresh = 1.35d;
				double heightDiff = victim.getLocation().getBlockY() - shooter.getLocation().getBlockY();
				if (heightDiff > 0) {
					headshotThresh -= Math.min(0.35, (heightDiff / 10));
				}
				//Disabled headshot if you are too close since it was buggy
				if (projectileHitY - headLocation > headshotThresh && projectile.getOrigin().distance(projectile.getLocation()) > 10) {
					// such a horrible damage system
					var fakeEvent = new EntityDamageByEntityEvent(player, shooter, EntityDamageEvent.DamageCause.PROJECTILE, 999);
					DamageEvent.createDamageEvent(fakeEvent);
					//Hitmarker Sound effect
					//shooter.playSound(shooter.getLocation(), Sound.ENTITY_ITEM_FRAME_PLACE, 2f, 2.0f);
				}
			}
		}

		//Sniper Rifle Shooting
		public void playerDropItem(PlayerDropItemEvent event) {
			Player player = event.getPlayer();
			Item item = event.getItemDrop();
			World world = player.getWorld();
			if (item.getItemStack().getType() == Material.SPYGLASS && !player.hasCooldown(Material.SPYGLASS)) {
				//Inaccuracy based on movement
				//player.getVelocity() sux so i will base movement on the player's state
				Location loc = player.getLocation();
				double inaccuracy = 0;
				if (player.isSprinting() || player.isGliding() || player.isJumping() ||
						loc.subtract(0, 1, 0).getBlock().getType() == Material.AIR ||
						player.getVelocity().lengthSquared() > 1) {
					inaccuracy = 2;
				} else if (player.isInWater() || player.isSwimming()) {
					inaccuracy = 1;
				}
				Vector velocity = loc.getDirection();
				var random = new Vector(gunInaccuracy.nextGaussian(), gunInaccuracy.nextGaussian(), gunInaccuracy.nextGaussian());
				random.normalize().multiply(inaccuracy);
				velocity.add(random);
				//Shooting Rifle + Arrow Properties
				Arrow arrow = player.launchProjectile(Arrow.class, velocity);
				arrow.setShotFromCrossbow(true);
				arrow.setGravity(false);
				arrow.setKnockbackStrength(1);
				arrow.setCritical(false);
				arrow.setPickupStatus(PickupStatus.DISALLOWED);
				arrow.setPierceLevel(100);
				arrow.setDamage(1);
				world.playSound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 2.5f);

				//Sniper Cooldown + deleting the dropped sniper and returning a new one.
				player.setCooldown(Material.SPYGLASS, 50);
				event.setCancelled(true);
			}
		}

		@Override
		public void onPlayerTick(Player player) {
			var uuid = player.getUniqueId();
			float exp = player.getExp();
			World world = player.getWorld();
			var inventory = player.getInventory();

			//Grenade Cooldown
			if (exp == 0.999f) {

			} else if (exp + 0.005f >= 1) {
				player.setExp(0.999f);
			} else {
				player.setExp(exp + 0.005f);
			}

			//Sniper Information message
			if (inventory.getItemInMainHand().getType() == Material.SPYGLASS) {
				Component actionBar = Component.text("Drop Spyglass in hand to shoot").color(SNIPER_MSG_COLOR);
				Component text = Component.text("Drop Spyglass in your main hand to shoot").color(SNIPER_MSG_COLOR);
				PlayerInfo pinfo = Main.getPlayerInfo(player);
				if (pinfo.getPreference(Preferences.KIT_ACTION_BAR)) {
					player.sendActionBar(actionBar);
				}
				//Chat Message is only sent once per life
				if (pinfo.getPreference(Preferences.KIT_CHAT_MESSAGES) && RECEIVED_SNIPER_CHAT_MESSAGE.add(uuid)) {
					player.sendMessage(text);
				}
			}

			//Grenade Information message
			if (inventory.getItemInMainHand().getType() == Material.TURTLE_HELMET && player.getExp() == 0.999f) {

				Component actionBar = Component.text("Left/Right Click to Arm").color(GRENADE_MSG_COLOR);
				Component text = Component.text("Click to arm the grenade").color(GRENADE_MSG_COLOR);
				PlayerInfo pinfo = Main.getPlayerInfo(player);
				if (pinfo.getPreference(Preferences.KIT_ACTION_BAR)) {
					player.sendActionBar(actionBar);
				}
				//Chat Message is only sent once per life
				if (pinfo.getPreference(Preferences.KIT_CHAT_MESSAGES) && RECEIVED_GRENADE_CHAT_MESSAGE.add(uuid)) {
					player.sendMessage(text);
				}
			}

			//Grenade Fail Check
			//Check if inventory has any grenades, maybe update later to allow for admin abuse grenade spam
			if (inventory.getHelmet() != null && inventory.getHelmet().getType() == Material.TURTLE_HELMET) {
				player.sendMessage(Component.text("Please do not wear the grenade on your head. Thank you.", GRENADE_MSG_COLOR));

				world.createExplosion(player.getLocation(), 2.5f, false, false);
				var fakeEvent = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, 999);
				DamageEvent.createDamageEvent(fakeEvent, DamageType.EXPLOSION);
			} else if (player.getCooldown(Material.TURTLE_HELMET) == 1 && inventory.contains(Material.TURTLE_HELMET)) {
				world.createExplosion(player.getLocation(), 2.5f, false, false);
				var fakeEvent = new EntityDamageByEntityEvent(player, player, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, 999);
				DamageEvent.createDamageEvent(fakeEvent, DamageType.SNIPER_GRENADE_FAIL);
			}
			//Sniper Reload Sound
			if (player.getCooldown(Material.SPYGLASS) == 15) {
				player.playSound(player, Sound.ITEM_ARMOR_EQUIP_CHAIN, 2f, 0.8f);
			}
		}
	}
}
