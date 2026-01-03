package de.marsg.wishlist.wishlist.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter{

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    @Override
    public String format(final LogRecord logRecord) {
        return String.format(
                "%1$s %2$-7s %3$s%n",
                new SimpleDateFormat(PATTERN).format(
                        new Date(logRecord.getMillis())),
                        logRecord.getLevel().getName(), formatMessage(logRecord));
    }
}
