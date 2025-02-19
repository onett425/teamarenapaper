package me.toomuchzelda.teamarenapaper.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.HSVLike;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextUtils {

	public static final DecimalFormat ONE_DECIMAL_POINT = new DecimalFormat("0.#");
	public static final DecimalFormat TWO_DECIMAL_POINT = new DecimalFormat("0.##");
	/**
	 * For item lores that have "left click to do x" and/or "right click to do y"
	 */
	public static final TextColor LEFT_CLICK_TO = TextColor.color(66, 135, 245);
	public static final TextColor RIGHT_CLICK_TO = TextColor.color(10, 135, 255);

	public static TextComponent formatDuration(@NotNull Duration duration) {
		long days = duration.toDaysPart();
		int hours = duration.toHoursPart();
		int minutes = duration.toMinutesPart();
		double remainingSeconds = duration.toSecondsPart() + duration.getNano() / 1_000_000_000d;
		var string = new StringBuilder();
		if (days != 0)
			string.append(days).append('d');
		if (hours != 0)
			string.append(hours).append('h');
		if (minutes != 0)
			string.append(minutes).append('m');
		if (remainingSeconds != 0)
			string.append(ONE_DECIMAL_POINT.format(remainingSeconds)).append('s');
		return Component.text(string.length() == 0 ? "just now" : string.toString(), NamedTextColor.YELLOW);
	}

	public static TextComponent formatDuration(@NotNull Duration duration, @NotNull ZonedDateTime time, @Nullable Locale locale) {
		var timeString = time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
				.localizedBy(locale == null ? Locale.getDefault() : locale));
		return formatDuration(duration)
				.hoverEvent(HoverEvent.showText(Component.text(timeString, NamedTextColor.YELLOW)));
	}

	public static final Style PLAIN_STYLE = Style.style(builder -> {
		builder.color(NamedTextColor.WHITE);
		for (var decoration : TextDecoration.values()) {
			builder.decoration(decoration, TextDecoration.State.FALSE);
		}
	});

	public static int measureWidth(int codePoint) {
		var font = MinecraftFont.Font;
		if (Character.isBmpCodePoint(codePoint)) {
			var sprite = font.getChar((char) codePoint);
			if (sprite != null) { // has Bukkit sprite data
				return sprite.getWidth();
			}
		}
		return 8; // worst case
	}

	public static int measureWidth(CharSequence string) {
		int result = 0;
		int[] codePoints = string.codePoints().toArray();
		for (int codePoint : codePoints) {
			result += measureWidth(codePoint);
		}
		// 1px gap or something
		result += codePoints.length - 1;
		return result;
	}


	/**
	 * Create a bloated gradient component
	 *
	 * @param string The string.
	 * @param from   The lower-bound text color.
	 * @param to     The upper-bound text color.
	 * @param style  The style to inherit from.
	 * @param offset The progress offset (0 - 1)
	 * @return An unnecessarily bloated {@link Component}
	 * @author jacky8399
	 */
	public static Component getUselessRGBText(String string, TextColor from, TextColor to, Style style, double offset) {
		var builder = Component.text().style(style);
		int totalWidth = measureWidth(string);
		int width = (int) (totalWidth * offset);
		int[] codePoints = string.codePoints().toArray();
		for (int codePoint : codePoints) {
			float progress = ((float) width / totalWidth) % 1f;
			width += measureWidth(codePoint) + 1;
			if (Character.isSpaceChar(codePoint)) {
				builder.append(Component.text(Character.toString(codePoint)));
			} else {
				var color = TextColor.lerp(progress, from, to);
				builder.append(Component.text(Character.toString(codePoint), color));
			}
		}
		return builder.build();
	}

	/**
	 * Create a bloated gradient component
	 *
	 * @param string The string.
	 * @param from   The lower-bound text color.
	 * @param to     The upper-bound text color.
	 * @return An unnecessarily bloated {@link Component}
	 * @author jacky8399
	 */
	public static Component getUselessRGBText(String string, TextColor from, TextColor to) {
		return getUselessRGBText(string, from, to, Style.empty(), 0);
	}

	/**
	 * Create a bloated "rainbow" component
	 *
	 * @param string The string.
	 * @param style  The style to inherit from.
	 * @param offset The progress offset (0 - 1)
	 * @return An unnecessarily bloated {@link Component}
	 * @author jacky8399
	 */
	public static Component getUselessRainbowText(String string, Style style, double offset) {
		var builder = Component.text().style(style);
		int totalWidth = measureWidth(string);
		int width = (int) (totalWidth * offset);
		int[] codePoints = string.codePoints().toArray();
		for (int codePoint : codePoints) {
			float progress = ((float) width / totalWidth) % 1f;
			width += measureWidth(codePoint);
			if (Character.isSpaceChar(codePoint)) {
				builder.append(Component.text(Character.toString(codePoint)));
			} else {
				var color = TextColor.color(HSVLike.hsvLike(progress, 1, 1));
				builder.append(Component.text(Character.toString(codePoint), color));
			}
		}
		return builder.build().compact();
	}

	/**
	 * Create a bloated "rainbow" component
	 *
	 * @param string The string.
	 * @return An unnecessarily bloated {@link Component}
	 * @author jacky8399
	 */
	public static Component getUselessRainbowText(String string) {
		return getUselessRainbowText(string, Style.empty(), 0);
	}

	public static Component getProgressText(String string, Style background, Style cursor, Style foreground, double progress) {
		if (progress >= 1) {
			return Component.textOfChildren(Component.text(string, foreground));
		} else if (progress < 0) {
			return Component.textOfChildren(Component.text(string, background));
		}

		int maxWidth = measureWidth(string);
		int lastWidth = 0;
		int[] codePoints = string.codePoints().toArray();
		var foregroundString = new StringBuilder();
		int i = 0;
		for (; i < codePoints.length; i++) {
			int codePoint = codePoints[i];

			int newWidth = lastWidth + measureWidth(codePoint);
			double distance = Math.abs((double) newWidth / maxWidth - progress);
			double lastDistance = Math.abs((double) lastWidth / maxWidth - progress);
			if (distance >= lastDistance) {
				var backgroundString = new StringBuilder();
				while (++i != codePoints.length) { // forEachRemaining
					backgroundString.appendCodePoint(codePoints[i]);
				}
				return Component.textOfChildren(
						Component.text(foregroundString.toString(), foreground),
						Component.text(Character.toString(codePoint), cursor),
						Component.text(backgroundString.toString(), background)
				);
			} else {
				foregroundString.appendCodePoint(codePoint);
				lastWidth = newWidth;
			}
		}
		return Component.textOfChildren(Component.text(string, foreground));
	}

	public static Component getProgressText(String string, TextColor backgroundColor, TextColor cursorColor, TextColor foregroundColor, double progress) {
		return getProgressText(string, Style.style(backgroundColor), Style.style(cursorColor), Style.style(foregroundColor), progress);
	}

	private static final String[] PROGRESS_BLOCK = {"▏", "▎", "▍", "▌", "▋", "▊", "▉", "█"};

	public static String getProgressBlock(double progress) {
		return PROGRESS_BLOCK[MathUtils.clamp(0, 7, (int) Math.round(progress / 0.125))];
	}

	/**
	 * Create a smoother progress bar
	 */
	public static Component getProgressBar(Style background, Style foreground, int blocks, double progress) {
		if (blocks <= 0)
			throw new IllegalArgumentException("blocks must be > 0");
		if (progress >= 1)
			return Component.text(PROGRESS_BLOCK[7].repeat(blocks), foreground);
		else if (progress <= 0)
			return Component.text(PROGRESS_BLOCK[7].repeat(blocks), background);

		var builder = Component.text();
		double increment = 1d / blocks;
		// blocks fully behind the progress
		int blocksBehind = (int) (progress / increment);
		builder.append(Component.text(PROGRESS_BLOCK[7].repeat(blocksBehind), foreground));
		double localProgress = progress % increment / increment;
		if (localProgress != 0) {
			int eightsBehind = (int) Math.round(localProgress / 0.125);
			// check if close enough to one of the blocks
			if (eightsBehind == 8) {
				builder.append(Component.text(PROGRESS_BLOCK[7], foreground));
			} else if (eightsBehind == 0) {
				builder.append(Component.text(PROGRESS_BLOCK[7], background));
			} else {
				builder.append(Component.text(PROGRESS_BLOCK[eightsBehind - 1], foreground),
						Component.text(PROGRESS_BLOCK[7 - eightsBehind], background));
			}
		}
		// blocks fully ahead of the progress
		int blocksAhead = (int) ((1 - progress) / increment);
		builder.append(Component.text(PROGRESS_BLOCK[7].repeat(blocksAhead), background));
		return builder.build();
	}

	public static Component getProgressBar(TextColor backgroundColor, TextColor foregroundColor, int blocks, double progress) {
		return getProgressBar(Style.style(backgroundColor), Style.style(foregroundColor), blocks, progress);
	}

	public static Component getRGBManiacComponent(Component component, Style style, double offset) {
		var builder = Component.text();
		style = style.merge(component.style());
		if (component instanceof TextComponent text) {
			Component rgbComponent;
			var color = style.color();
			if (color == NamedTextColor.WHITE || color == null) {
				rgbComponent = getUselessRainbowText(text.content(), Style.empty(), offset);
			} else {
				var awtColor = new Color(color.value());
				var background = TextColor.color(awtColor.darker().getRGB());
				var foreground = TextColor.color(awtColor.brighter().brighter().getRGB());
				rgbComponent = getProgressText(text.content(), background, foreground, background,
						offset % 1d);
			}
			builder.append(text.style(style).content("").children(rgbComponent.children()));
		} else {
			builder.append(component.style(style));
		}
		var children = component.children();
		for (var child : children) {
			var newChild = getRGBManiacComponent(child, style, offset);
			builder.append(newChild);
		}
		return builder.build();
	}

	public static Title createTitle(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		return Title.title(title, subtitle, Title.Times.times(
				Duration.ofMillis(fadeInTicks * 50L),
				Duration.ofMillis(stayTicks * 50L),
				Duration.ofMillis(fadeOutTicks * 50L)
		));
	}

	private static final Pattern SAFE_TO_WRAP = Pattern.compile("\\s|\\n");
	public static List<Component> wrapString(String string, Style style, int maxWidth) {
		List<Component> lines = new ArrayList<>();
		StringJoiner line = new StringJoiner(" ");
		for (String word : SAFE_TO_WRAP.split(string)) {
			// arbitrary width
			if (TextUtils.measureWidth(line.toString()) < maxWidth) {
				line.add(word);
			} else {
				lines.add(Component.text(line.toString(), style));
				line = new StringJoiner(" ");
				line.add(word);
			}
		}
		// final line
		lines.add(Component.text(line.toString(), style));
		return Collections.unmodifiableList(lines);
	}

	public static List<Component> toLoreList(String string, Style style, TagResolver... tagResolvers) {
		// TODO this won't create a new object, but use decorationIfAbsent when Adventure is updated
		Style styleNoItalics = style.decoration(TextDecoration.ITALIC, false);
		MiniMessage miniMessage = MiniMessage.builder()
				.postProcessor(component -> component.compact().style(styleNoItalics))
				.build();
		return string.lines()
			.map(line -> {
				if (line.isEmpty())
					return Component.empty();
				else if (line.indexOf('<') > -1)
					return miniMessage.deserialize(line, tagResolvers);
				else
					return Component.text(line, styleNoItalics);
			})
			.collect(Collectors.toList());
	}

	public static List<Component> toLoreList(String string, TextColor textColor, TagResolver... tagResolvers) {
		return toLoreList(string, Style.style(textColor), tagResolvers);
	}

	public static List<Component> toLoreList(String string, TagResolver... tagResolvers) {
		return toLoreList(string, Style.empty(), tagResolvers);
	}

}
