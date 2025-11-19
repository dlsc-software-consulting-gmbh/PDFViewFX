package com.dlsc.pdfviewfx;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

public class Translations {

    private Properties languageProperties = new Properties();

    public String getTranslation(String languageString, Object ... arguments) {
        if(languageProperties.isEmpty()){
            loadLanguageProperties();
        }
        String message = languageString;
        if (languageProperties != null) {
            if (languageProperties.containsKey(languageString)) {
                message = languageProperties.getProperty(languageString);
            }
        }
        return new MessageFormat(message).format(arguments);
    }

    private void loadLanguageProperties() {
        URL propertiesPath = null;
        try {
            propertiesPath = getClass().getResource(getPropertiesFileName(Locale.getDefault().getLanguage()));
            languageProperties.load(new FileInputStream(propertiesPath.getPath()));
        } catch (Exception e) {
            System.err.println("Language.properties with URL '" + propertiesPath  + "' could not be loaded: " + e.getMessage());
        }
    }

    private static String getPropertiesFileName(String language) {
        String propertiesFileName = "language.properties";

        if(language.toLowerCase().contains("de")){
            propertiesFileName = "language_de.properties";
        } else if(language.toLowerCase().contains("fr")){
            propertiesFileName = "language_fr.properties";
        }

        return propertiesFileName;
    }
}
