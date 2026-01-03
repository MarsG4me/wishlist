package de.marsg.wishlist.wishlist.configs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.springframework.stereotype.Component;

@Component
public class ConfigMgr {

    private Properties props = new Properties();

    public ConfigMgr() {
        File configFile = new File("./config.properties");
        if (configFile.exists()) {

            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config", e);
            }

        } else {
            createDefaultConfig(configFile);
        }
    }

    public String getProperty(String key) {
        return props.getProperty(key);
    }

    private void createDefaultConfig(File configFile) {
        System.out.println("Creating default config file...");
        Properties defaults = new Properties();

        defaults.setProperty("db.jdbc_url", "jdbc:postgresql://localhost:5432/wishlist");
        defaults.setProperty("db.username", "user");
        defaults.setProperty("db.password", "password");

        try (FileOutputStream out = new FileOutputStream(configFile)) {
            defaults.store(out, "Default config");
        System.out.println("Created default config file.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }

    }
}
