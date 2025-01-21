package it.nicoloscialpi.mazegenerator.themes;

import java.util.HashMap;

public class Themes {

    private static HashMap<String, Theme> themes;

    public static HashMap<String, Theme> getThemes() {
        return themes;
    }

    public static void parseThemesFromReader(ThemeConfigurationReader themeConfigurationReader) {
        themes = themeConfigurationReader.parseThemes();
    }

    public static Theme getTheme(String name) {
        if (themes == null) {
            return new Theme();
        }
        return themes.getOrDefault(name, new Theme());
    }
}