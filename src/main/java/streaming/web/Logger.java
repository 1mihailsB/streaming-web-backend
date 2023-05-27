package streaming.web;

import java.util.logging.Level;

public class Logger {
    private java.util.logging.Logger logger;

    public Logger(String className) {
        logger = java.util.logging.Logger.getLogger(className);
        
    }

    public void info(String logString) {
        logger.log(Level.INFO, "\u001B[32m" + logString + "\u001B[32m");
    }
}
