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
  @Mock ScopedSpan scopedSpan;
  ErrorParser parser = new ErrorParser();

  @Test public void error_customizer() {
    parser.error(new RuntimeException("this cake is a lie"), customizer);

    verify(customizer).tag("error", "this cake is a lie");
  }

  @Test public void error_scopedSpan() {
    parser.error(new RuntimeException("this cake is a lie"), scopedSpan);

    verify(scopedSpan).tag("error", "this cake is a lie");
  }

  @Test public void error_noMessage_customizer() {
    parser.error(new RuntimeException(), customizer);

    verify(customizer).tag("error", "RuntimeException");
  }

  @Test public void error_noMessage_scopedSpan() {
    parser.error(new RuntimeException(), scopedSpan);

    verify(scopedSpan).tag("error", "RuntimeException");
  }

  @Test public void error_noop_customizer() {
    ErrorParser.NOOP.error(new RuntimeException("this cake is a lie"), customizer);

    verifyNoMoreInteractions(customizer);
  }

  @Test public void error_noop_scopedSpan() {
    ErrorParser.NOOP.error(new RuntimeException("this cake is a lie"), scopedSpan);

    verifyNoMoreInteractions(scopedSpan);
  }
}
