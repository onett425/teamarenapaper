package me.toomuchzelda.teamarenapaper.teamarena.preferences;


import java.util.LinkedList;

public class PreferenceDamageTilt extends Preference<Boolean>
{
	public PreferenceDamageTilt() {
		super(EnumPreference.getId(), "DamageTilt", "Whether your screen should tilt in pain when taking damage. Due to a limitation this will only work if HEARTS_FLASH_DAMAGE is also set to false.", true, Preference.BOOLEAN_SUGGESTIONS);
	}
}
