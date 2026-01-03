package de.marsg.wishlist.wishlist.logging;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class LogMgr {

    private Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    @PostConstruct
    public boolean setup() {

        // get file logger ready for usage
        try {
            // remove all active Handlers
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }
            logger.setUseParentHandlers(false);

            // Ensure the logs directory exists
            File logDirectory = new File("./logs");
            if (!logDirectory.exists() && !logDirectory.mkdirs()) {
                logger.severe("Failed to create log directory.");
                return false;
            }
            // Create own handlers with specified format

            // put files in a separate folder, make each file max size of 20MiB (20971520
            // Bytes) and keep the last 5 files,
            // after reboot append to last file
            FileHandler fileTxt = new FileHandler("./logs/logs%g.log", 20971520, 5, true);
            fileTxt.setFormatter(new LogFormatter());
            fileTxt.setLevel(Level.FINE);
            logger.addHandler(fileTxt);

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new LogFormatter());
            consoleHandler.setLevel(Level.FINE);
            logger.addHandler(consoleHandler);

            logInfo("Backend : [LogMgr] Logger is ready for usage.");
            return true;
        } catch (SecurityException e) {
            logger.severe("Security error while trying to load Logger file: "+e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            logger.severe("Error while trying to load Logging.txt file: "+e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void logFine(String log) {
        logger.fine(log);
    }

    public void logFine(String log, Object... args) {
        logFine(String.format(log, args));
    }

    public void logInfo(String log) {
        logger.info(log);
    }

    public void logInfo(String log, Object... args) {
        logInfo(String.format(log, args));
    }

    public void logWarning(String log) {
        logger.warning(log);
    }

    public void logWarning(String log, Object... args) {
        logWarning(String.format(log, args));
    }

    public void logSevere(String log) {
        logger.severe(log);
    }

    public void logSevere(String log, Object... args) {
        logSevere(String.format(log, args));
    }

    public void logSevere(String log, Throwable t) {
        logger.log(Level.SEVERE, log, t);
    }
}
