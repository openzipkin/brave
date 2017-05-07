package brave.p6spy;

import java.io.OutputStream;

public class DerbyUtils {

  //Get rid of the annoying derby.log file
  public static void disableLog() {
    System.setProperty("derby.stream.error.field", DerbyUtils.class.getName() + ".DEV_NULL");
  }

  public static final OutputStream DEV_NULL = new OutputStream() {
    public void write(int b) {
    }
  };
}
