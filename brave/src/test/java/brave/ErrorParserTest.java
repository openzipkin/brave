package brave;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class ErrorParserTest {
  @Mock SpanCustomizer customizer;
  ErrorParser parser = new ErrorParser();

  @Test public void error() {
    parser.error(new RuntimeException("this cake is a lie"), customizer);

    verify(customizer).tag("error", "this cake is a lie");
  }

  @Test public void error_noMessage() {
    parser.error(new RuntimeException(), customizer);

    verify(customizer).tag("error", "RuntimeException");
  }

  @Test public void error_noop() {
    ErrorParser.NOOP.error(new RuntimeException("this cake is a lie"), customizer);

    verifyNoMoreInteractions(customizer);
  }
}
