package com.jreq.request.application;

import com.jreq.request.application.CollectionImportDocument.CollectionItem;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestAuthentication;
import com.jreq.request.domain.RequestBodyType;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionImportMapperTest {
    private static final String SCHEMA =
            "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

    private final CollectionImportParser parser = new CollectionImportParser();
    private final CollectionImportMapper mapper = new CollectionImportMapper();

    @Test
    void flattensFoldersIntoRequestNames() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Auth","item":[
                    {"name":"Login","request":{"method":"POST","url":"https://example.com/login"}},
                    {"name":"Logout","request":{"method":"POST","url":"https://example.com/logout"}}
                  ]},
                  {"name":"Ping","request":{"method":"GET","url":"https://example.com/ping"}}
                ]}
                """);

        assertThat(names(mapping.importedCollection().requests()))
                .containsExactly("Auth / Login", "Auth / Logout", "Ping");
        assertThat(mapping.skippedRequestCount()).isZero();
        assertThat(mapping.importedCollection().requests())
                .allSatisfy(request -> assertThat(request.location())
                        .isEqualTo(RequestLocation.collection(
                                mapping.importedCollection().collection().id())));
    }

    @Test
    void extractsQueryParametersFromUrlObjectsAndStripsThemFromTheUrl() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Search","request":{"method":"GET","url":{
                    "raw":"https://example.com/users?page=1&size=20",
                    "protocol":"https","host":["example","com"],"path":["users"],
                    "query":[
                      {"key":"page","value":"1"},
                      {"key":"size","value":"20","disabled":true},
                      {"key":"","value":"noise"}
                    ]}}}
                ]}
                """);

        SavedRequest request = onlyRequest(mapping);
        assertThat(request.definition().url()).isEqualTo("https://example.com/users");
        assertThat(request.definition().queryParameters())
                .extracting(KeyValueEntry::key, KeyValueEntry::value, KeyValueEntry::enabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("page", "1", true),
                        org.assertj.core.groups.Tuple.tuple("size", "20", false));
    }

    @Test
    void keepsAStringUrlUntouched() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Ping","request":{"method":"GET",
                    "url":"https://example.com/ping?full=true"}}
                ]}
                """);

        SavedRequest request = onlyRequest(mapping);
        assertThat(request.definition().url()).isEqualTo("https://example.com/ping?full=true");
        assertThat(request.definition().queryParameters()).isEmpty();
    }

    @Test
    void reconstructsTheUrlWhenRawIsMissing() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Ping","request":{"method":"GET","url":{
                    "protocol":"https","host":["api","example","com"],"path":["v1","ping"]}}}
                ]}
                """);

        assertThat(onlyRequest(mapping).definition().url())
                .isEqualTo("https://api.example.com/v1/ping");
    }

    @Test
    void defaultsToGetWhenTheMethodIsMissing() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Ping","request":{"url":"https://example.com/ping"}}
                ]}
                """);

        assertThat(onlyRequest(mapping).definition().method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void skipsRequestsWithUnsupportedMethodsOrMissingUrls() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Strange","request":{"method":"QUERY","url":"https://example.com"}},
                  {"name":"NoUrl","request":{"method":"GET"}},
                  {"name":"Ok","request":{"method":"GET","url":"https://example.com"}}
                ]}
                """);

        assertThat(names(mapping.importedCollection().requests())).containsExactly("Ok");
        assertThat(mapping.skippedRequestCount()).isEqualTo(2);
        assertThat(mapping.warnings())
                .anySatisfy(warning -> assertThat(warning.message())
                        .contains("Strange").contains("QUERY"))
                .anySatisfy(warning -> assertThat(warning.message())
                        .contains("NoUrl").contains("no URL"));
    }

    @Test
    void mapsBodyModesBestEffort() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Json","request":{"method":"POST","url":"https://example.com/a",
                    "body":{"mode":"raw","raw":"{\\"a\\":1}","options":{"raw":{"language":"json"}}}}},
                  {"name":"Text","request":{"method":"POST","url":"https://example.com/b",
                    "body":{"mode":"raw","raw":"hello"}}},
                  {"name":"Form","request":{"method":"POST","url":"https://example.com/c",
                    "body":{"mode":"urlencoded","urlencoded":[
                      {"key":"a","value":"b c"},{"key":"x","value":"1","disabled":true}]}}},
                  {"name":"Upload","request":{"method":"POST","url":"https://example.com/d",
                    "body":{"mode":"formdata","formdata":[{"key":"f","type":"file"}]}}}
                ]}
                """);

        List<SavedRequest> requests = mapping.importedCollection().requests();
        assertThat(requests.get(0).definition().body().type()).isEqualTo(RequestBodyType.JSON);
        assertThat(requests.get(0).definition().body().content()).isEqualTo("{\"a\":1}");
        assertThat(requests.get(1).definition().body().type()).isEqualTo(RequestBodyType.RAW_TEXT);
        assertThat(requests.get(2).definition().body().type()).isEqualTo(RequestBodyType.RAW_TEXT);
        assertThat(requests.get(2).definition().body().content()).isEqualTo("a=b+c");
        assertThat(requests.get(2).definition().body().contentType())
                .isEqualTo("application/x-www-form-urlencoded");
        assertThat(requests.get(3).definition().body().type()).isEqualTo(RequestBodyType.NONE);
        assertThat(mapping.warnings()).anySatisfy(warning -> assertThat(warning.message())
                .contains("Upload").contains("formdata"));
    }

    @Test
    void mapsSupportedAuthAndDowngradesUnsupportedAuthWithWarnings() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Basic","request":{"method":"GET","url":"https://example.com/a",
                    "auth":{"type":"basic","basic":[
                      {"key":"username","value":"u"},{"key":"password","value":"p"}]}}},
                  {"name":"Bearer","request":{"method":"GET","url":"https://example.com/b",
                    "auth":{"type":"bearer","bearer":[{"key":"token","value":"t"}]}}},
                  {"name":"OAuth","request":{"method":"GET","url":"https://example.com/c",
                    "auth":{"type":"oauth2","oauth2":[{"key":"accessToken","value":"secret"}]}}}
                ]}
                """);

        List<SavedRequest> requests = mapping.importedCollection().requests();
        assertThat(requests.get(0).definition().authentication())
                .isEqualTo(new RequestAuthentication.Basic("u", "p"));
        assertThat(requests.get(1).definition().authentication())
                .isEqualTo(new RequestAuthentication.JwtBearer("t"));
        assertThat(requests.get(2).definition().authentication())
                .isEqualTo(RequestAuthentication.none());
        assertThat(mapping.warnings()).anySatisfy(warning -> assertThat(warning.message())
                .contains("oauth2").contains("without authentication")
                .doesNotContain("secret"));
    }

    @Test
    void inheritsCollectionLevelAuthUnlessTheRequestOverridesIt() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},
                 "auth":{"type":"bearer","bearer":[{"key":"token","value":"collection-token"}]},
                 "item":[
                  {"name":"Inherit","request":{"method":"GET","url":"https://example.com/a"}},
                  {"name":"Override","request":{"method":"GET","url":"https://example.com/b",
                    "auth":{"type":"noauth"}}}
                ]}
                """);

        List<SavedRequest> requests = mapping.importedCollection().requests();
        assertThat(requests.get(0).definition().authentication())
                .isEqualTo(new RequestAuthentication.JwtBearer("collection-token"));
        assertThat(requests.get(1).definition().authentication())
                .isEqualTo(RequestAuthentication.none());
    }

    @Test
    void deduplicatesRequestNamesCaseInsensitively() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Same","request":{"method":"GET","url":"https://example.com/a"}},
                  {"name":"same","request":{"method":"GET","url":"https://example.com/b"}},
                  {"name":"SAME","request":{"method":"GET","url":"https://example.com/c"}}
                ]}
                """);

        assertThat(names(mapping.importedCollection().requests()))
                .containsExactly("Same", "same (2)", "SAME (3)");
    }

    @Test
    void fallsBackToAGenericNameWhenTheRequestNameIsBlank() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"  ","request":{"method":"GET","url":"https://example.com/a"}}
                ]}
                """);

        assertThat(names(mapping.importedCollection().requests())).containsExactly("Request");
    }

    @Test
    void importsCollectionVariablesAsACollectionScopedEnvironment() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[],
                 "variable":[
                   {"key":"host","value":"https://api.example.com"},
                   {"key":"legacy","value":"x","disabled":true},
                   {"key":"host","value":"duplicate"},
                   {"key":"  ","value":"blank"},
                   {"key":"port","value":8080}
                 ]}
                """);

        List<RequestEnvironment> environments = mapping.importedCollection().environments();
        assertThat(environments).hasSize(1);
        RequestEnvironment environment = environments.getFirst();
        assertThat(environment.name()).isEqualTo(CollectionImportMapper.IMPORTED_ENVIRONMENT_NAME);
        assertThat(environment.scope()).isEqualTo(
                EnvironmentScope.collection(mapping.importedCollection().collection().id()));
        assertThat(environment.variables())
                .extracting("key", "value", "enabled", "secret")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("host", "https://api.example.com", true, false),
                        org.assertj.core.groups.Tuple.tuple("legacy", "x", false, false),
                        org.assertj.core.groups.Tuple.tuple("port", "8080", true, false));
        assertThat(mapping.warnings()).anySatisfy(warning -> assertThat(warning.message())
                .contains("variable").contains("skipped"));
    }

    @Test
    void skipsItemsThatAreNotRequests() {
        CollectionImportMapper.CollectionImportMapping mapping = map("""
                {"info":{"name":"API","schema":"%s"},"item":[
                  {"name":"Broken"},
                  {"name":"LegacyString","request":"https://example.com/legacy"},
                  {"name":"Ok","request":{"method":"GET","url":"https://example.com"}}
                ]}
                """);

        assertThat(names(mapping.importedCollection().requests())).containsExactly("Ok");
        assertThat(mapping.skippedRequestCount()).isEqualTo(2);
    }

    private CollectionImportMapper.CollectionImportMapping map(String jsonTemplate) {
        String json = jsonTemplate.formatted(SCHEMA);
        return mapper.map(parser.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    private SavedRequest onlyRequest(CollectionImportMapper.CollectionImportMapping mapping) {
        assertThat(mapping.importedCollection().requests()).hasSize(1);
        return mapping.importedCollection().requests().getFirst();
    }

    private List<String> names(List<SavedRequest> requests) {
        return requests.stream().map(request -> request.definition().name()).toList();
    }
}
