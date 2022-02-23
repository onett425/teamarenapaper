package me.toomuchzelda.teamarenapaper.core;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

import java.util.Objects;
import java.util.Random;

public class MathUtils
{
	public static final Random random = new Random();
	
	public static double randomRange(double min, double max) {
		double rand = random.nextDouble() * (max - min);
		rand += min;
		return rand;
	}
	
	public static int randomMax(int max) {
		// + 1 to not exclude the max value itself
		return random.nextInt(max + 1);
	}
	
	public static int randomRange(int min, int max) {
		return randomMax(max) + min;
	}
	
	public static void shuffleArray(Object[] array) {
		for(int i = 0; i < array.length; i++) {
			int rand = MathUtils.randomMax(array.length - 1);
			Object temp = array[rand];
			array[rand] = array[i];
			array[i] = temp;
		}
	}
	
	public static boolean arrayContains(Object[] array, Object object) {
		for(Object obj : array) {
			if(Objects.equals(obj, object)) {
				return true;
			}
		}
		
		return false;
	}
	
	public static int clamp(int min, int max, int value) {
		return Math.max(Math.min(value, max), min);
	}
	
	public static double clamp(double min, double max, double value) {
		return Math.max(Math.min(value, max), min);
	}

	public static float clamp(float min, float max, float value) {
		return Math.max(Math.min(value, max), min);
	}
	
	public static Color randomColor() {
		int r = randomMax(255);
		int g = randomMax(255);
		int b = randomMax(255);
		return Color.fromRGB(r, g, b);
	}

	public static TextColor randomTextColor() {
		int r = randomMax(255);
		int g = randomMax(255);
		int b = randomMax(255);
		return TextColor.color(r, g, b);
	}
	
	//https://stackoverflow.com/questions/8911356/whats-the-best-practice-to-round-a-float-to-2-decimals/45772416#45772416
	//https://stackoverflow.com/a/35833800
	public static double round(double value, int scale) {
		return Math.round(value * Math.pow(10, scale)) / Math.pow(10, scale);
	}

}
