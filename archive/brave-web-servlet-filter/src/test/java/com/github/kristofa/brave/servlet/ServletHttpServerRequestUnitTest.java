package com.github.kristofa.brave.servlet;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.mockito.stubbing.Answer;


public class ServletHttpServerRequestUnitTest
{

    @Test
    public void passWithNonEncodedCharacters() {
        final HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURL()).thenAnswer((Answer<StringBuffer>) invocation ->
           new StringBuffer("https://мойДядя.самых/честных->правил/когда[Не]В~Шутку|Занемог/он УважатьСебяЗаставил")
        );

        when(request.getQueryString()).thenAnswer((Answer<String>) invocation ->
              "И=Лучше&Выдумать=Не мог"
        );

        ServletHttpServerRequest shsr = new ServletHttpServerRequest(request);

        try{
            shsr.getUri();
        }
        catch(Exception e){
            fail("Should not have thrown any exception, although got: " + e.getMessage());
        }

    }
}
