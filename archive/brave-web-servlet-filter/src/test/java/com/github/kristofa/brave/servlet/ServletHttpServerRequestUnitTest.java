package com.github.kristofa.brave.servlet;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ServletHttpServerRequestUnitTest {

    @Test
    public void passWithNonEncodedCharacters() {
        final HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURL()).thenAnswer(
                new Answer<StringBuffer>() {
                    @Override
                    public StringBuffer answer(InvocationOnMock invocationOnMock) throws Throwable {
                        return new StringBuffer("https://мойДядя.самых/честных->правил/когда[Не]В~Шутку|Занемог/он УважатьСебяЗаставил");
                    }
                }
        );

        when(request.getQueryString()).thenAnswer(
                new Answer<String>() {
                    @Override
                    public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                        return "И=Лучше&Выдумать=Не мог";
                    }
                }
        );

        ServletHttpServerRequest shsr = new ServletHttpServerRequest(request);

        try {
            shsr.getUri();
        } catch (Exception e) {
            fail("Should not have thrown any exception, although got: " + e.getMessage());
        }

    }
}
