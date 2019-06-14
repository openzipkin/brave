/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jersey.server;

import java.net.URI;
import java.util.List;
import java.util.regex.MatchResult;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.RuntimeResource;
import org.glassfish.jersey.uri.UriTemplate;

class FakeExtendedUriInfo implements ExtendedUriInfo {
  final URI baseURI;
  final List<UriTemplate> matchedTemplates;

  FakeExtendedUriInfo(URI baseURI, List<UriTemplate> matchedTemplates) {
    this.baseURI = baseURI;
    this.matchedTemplates = matchedTemplates;
  }

  @Override public Throwable getMappedThrowable() {
    return null;
  }

  @Override public List<MatchResult> getMatchedResults() {
    return null;
  }

  @Override public List<UriTemplate> getMatchedTemplates() {
    return matchedTemplates;
  }

  @Override public List<PathSegment> getPathSegments(String name) {
    return null;
  }

  @Override public List<PathSegment> getPathSegments(String name, boolean decode) {
    return null;
  }

  @Override public List<RuntimeResource> getMatchedRuntimeResources() {
    return null;
  }

  @Override public ResourceMethod getMatchedResourceMethod() {
    return null;
  }

  @Override public Resource getMatchedModelResource() {
    return null;
  }

  @Override public List<ResourceMethod> getMatchedResourceLocators() {
    return null;
  }

  @Override public List<Resource> getLocatorSubResources() {
    return null;
  }

  @Override public String getPath() {
    return null;
  }

  @Override public String getPath(boolean decode) {
    return null;
  }

  @Override public List<PathSegment> getPathSegments() {
    return null;
  }

  @Override public List<PathSegment> getPathSegments(boolean decode) {
    return null;
  }

  @Override public URI getRequestUri() {
    return null;
  }

  @Override public UriBuilder getRequestUriBuilder() {
    return null;
  }

  @Override public URI getAbsolutePath() {
    return null;
  }

  @Override public UriBuilder getAbsolutePathBuilder() {
    return null;
  }

  @Override public URI getBaseUri() {
    return baseURI;
  }

  @Override public UriBuilder getBaseUriBuilder() {
    return null;
  }

  @Override public MultivaluedMap<String, String> getPathParameters() {
    return null;
  }

  @Override public MultivaluedMap<String, String> getPathParameters(boolean decode) {
    return null;
  }

  @Override public MultivaluedMap<String, String> getQueryParameters() {
    return null;
  }

  @Override public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
    return null;
  }

  @Override public List<String> getMatchedURIs() {
    return null;
  }

  @Override public List<String> getMatchedURIs(boolean decode) {
    return null;
  }

  @Override public List<Object> getMatchedResources() {
    return null;
  }

  @Override public URI resolve(URI uri) {
    return null;
  }

  @Override public URI relativize(URI uri) {
    return null;
  }
}
