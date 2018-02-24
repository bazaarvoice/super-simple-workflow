package test.utils;

import com.bazaarvoice.sswf.Logger;
import scala.Function0;

public class StdOutLogger implements Logger {
    @Override public void trace(final Function0<String> message) {
        System.out.println("TRACE: "+message);
    }

    @Override public void warn(final Function0<String> message, final Throwable throwable) {
        System.out.println("WARN: "+message.apply());
        throwable.printStackTrace();
    }

    @Override public void warn(final Function0<String> message) {
        System.out.println("WARN: "+message.apply());
    }

    @Override public void error(final Function0<String> message, final Throwable throwable) {
        System.out.println("ERROR: "+message.apply());
        throwable.printStackTrace();
    }

    @Override public void error(final Function0<String> message) {
        System.out.println("ERROR: "+message.apply());
    }

    @Override public void debug(final Function0<String> message) {
        System.out.println("DEBUG: "+message.apply());
    }

    @Override public void info(final Function0<String> message) {
        System.out.println("INFO: "+message.apply());
    }
}
