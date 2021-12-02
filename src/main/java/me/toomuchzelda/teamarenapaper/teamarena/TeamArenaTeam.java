package me.toomuchzelda.teamarenapaper.teamarena;

import me.toomuchzelda.teamarenapaper.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class TeamArenaTeam
{
	private final String name;
	private final String simpleName;

	private final Color colour;
	//null if single flat colour
	private final Color secondColour;

	private final DyeColor dyeColour;
	private final TextColor RGBColour;
	
	//paper good spigot bad
	private Team paperTeam;
	
	private Location[] spawns;
	private Set<Entity> entityMembers = ConcurrentHashMap.newKeySet();
	
	//if someone needs to be booted out when a player leaves before game start
	//only used before teams decided
	public final Stack<Entity> lastIn = new Stack<>();
	
	//in the rare case a player joins during GAME_STARTING, need to find an unused spawn position
	// to teleport to
	public int spawnsIndex;
	
	//abstract score value, game-specific
	public int score;
	
	public TeamArenaTeam(String name, String simpleName, Color colour, Color secondColour, DyeColor dyeColor) {
		this.name = name;
		this.simpleName = simpleName;
		this.colour = colour;
		this.secondColour = secondColour;
		this.dyeColour = dyeColor;
		
		this.RGBColour = TextColor.color(colour.asRGB());
		
		spawns = null;
		score = 0;
		
		if(Bukkit.getScoreboardManager().getMainScoreboard().getTeam(name) != null)
			Bukkit.getScoreboardManager().getMainScoreboard().getTeam(name).unregister();
		
		paperTeam = Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam(name);
		paperTeam.displayName(Component.text(this.name).color(this.RGBColour));
		paperTeam.setAllowFriendlyFire(true);
		paperTeam.setCanSeeFriendlyInvisibles(true);
		paperTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
		paperTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
		paperTeam.color(NamedTextColor.nearestTo(this.RGBColour));
	}
	
	public String getName() {
		return name;
	}
	
	public String getSimpleName() {
		return simpleName;
	}
	
	public Color getColour() {
		return colour;
	}

	public boolean isGradient() {
		return secondColour != null;
	}

	public Color getSecondColour() {
		return secondColour;
	}
	
	public DyeColor getDyeColour() {
		return dyeColour;
	}
	
	public TextColor getRGBTextColor() {
		return RGBColour;
	}
	
	public static Color convert(NamedTextColor textColor) {
		return Color.fromRGB(textColor.red(), textColor.green(), textColor.blue());
	}
	
	public Team getPaperTeam() {
		return paperTeam;
	}
	
	public Location[] getSpawns() {
		return spawns;
	}
	
	public void setSpawns(Location[] array) {
		this.spawns = array;
		this.spawnsIndex = 0;
	}
	
	public void addMembers(Entity... entities) {
		for (Entity entity : entities)
		{
			if (entity instanceof Player player)
			{
				//if they're already on a team
				// remove them from that team and update the reference in their own class
				TeamArenaTeam team = Main.getPlayerInfo(player).team;
				if (team != null)
				{
					team.removeMembers(player);
				}
				Main.getPlayerInfo(player).team = this;
				//change tab list name to colour for RGB colours
				// and armor stand nametag

				//don't change name if it's not different
				// avoid sending packets and trouble
				{
					TextColor colour = TeamArena.noTeamColour;
					if (Main.getGame().showTeamColours)
						colour = this.getRGBTextColor();

					Component component = colourWord(player.getName());
					//mfw component doesnt have equals method
					if (!player.playerListName().contains(component)) {
						//Bukkit.broadcastMessage("Did not contain component");
						player.playerListName(component);
						Main.getPlayerInfo(player).nametag.setText(component, true);
					}
				}
				paperTeam.addEntry(player.getName());
			}
			else
			{
				paperTeam.addEntry(entity.getUniqueId().toString());
			}
			entityMembers.add(entity);
			lastIn.push(entity);
		}
	}

	public void removeMembers(Entity... entities) {
		for (Entity entity : entities)
		{
			if (entity instanceof Player player)
			{
				paperTeam.removeEntry(player.getName());
				Main.getPlayerInfo(player).team = null;
				//player.playerListName(Component.text(player.getName()).color(TeamArena.noTeamColour));
				// name colour should be handled by the team they're put on
			}
			else
			{
				paperTeam.removeEntry(entity.getUniqueId().toString());
			}
			entityMembers.remove(entity);
			lastIn.remove(entity);
		}
		Main.getGame().lastHadLeft = this;
	}
	
	public void removeAllMembers() {
		removeMembers(entityMembers.toArray(new Entity[0]));
	}

	public Set<String> getStringMembers() {
		return paperTeam.getEntries();
	}
	
	public Set<Entity> getEntityMembers() {
		return entityMembers;
	}

	//create gradient component word like player name or team name
	public Component colourWord(String str) {
		Component component = Component.empty();

		if(secondColour != null) {
			Bukkit.broadcastMessage("Secnd colour not null");
			for (float i = 0; i < str.length(); i++) {
				//percentage of second colour to use, leftover is percentage of first colour
				// from 0 to 1
				float percentage = (i / (float) str.length());

				Vector colour1 = new Vector(colour.getRed(), colour.getGreen(), colour.getBlue());
				Vector colour2 = new Vector(secondColour.getRed(), secondColour.getGreen(), secondColour.getBlue());

				colour1.multiply(1 - percentage);
				colour2.multiply(percentage);

				TextColor result = TextColor.color((int) (colour1.getX() + colour2.getX()),
						(int) (colour1.getY() + colour2.getY()),
						(int) (colour1.getZ() + colour2.getZ()));


				component = component.append(Component.text(str.charAt((int) i)).color(result));
			}
		}
		else {
			Bukkit.broadcastMessage("second colour null");
			component = Component.text(str).color(getRGBTextColor());
		}
		return component;
	}

	public static Color parseString(String string) {
		String[] strings = string.split(",");
		int[] ints = new int[3];
		for(int i = 0; i < strings.length; i++) {
			ints[i] = Integer.parseInt(strings[i]);
			if(i < 0 || i > 255) {
				throw new IllegalArgumentException("Bad colour info, must be between 0 and 255: " + i + " in " + string);
			}
		}
		return Color.fromRGB(ints[0], ints[1], ints[2]);
	}
}
