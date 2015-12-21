package example;

import com.bazaarvoice.sswf.Logger;
import scala.Function0;

public class StdOutLogger implements Logger {
    @Override public void trace(final Function0<String> message) {
        System.out.println("TRACE: "+message);
    }

    @Override public void warn(final Function0<String> message, final Throwable throwable) {
        System.out.println("WARN: "+message);

    }

    @Override public void warn(final Function0<String> message) {

        System.out.println("WARN: "+message);
    }

    @Override public void error(final Function0<String> message, final Throwable throwable) {

        System.out.println("ERROR: "+message);
    }

    @Override public void error(final Function0<String> message) {

        System.out.println("ERROR: "+message);
    }

    @Override public void debug(final Function0<String> message) {

        System.out.println("DEBUG: "+message);
    }

    @Override public void info(final Function0<String> message) {
        System.out.println("INFO: "+message);

    }
}
