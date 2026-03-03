package xyz.zcraft.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import xyz.zcraft.User;
import xyz.zcraft.elect.Round;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NetworkHelper {
    private static final String USER_INFO_URL = "https://1.tongji.edu.cn/api/sessionservice/session/getSessionUser";
    private static final String ROUNDS_URL = "https://1.tongji.edu.cn/api/electionservice/student/getRounds?projectId=1";
    private static final String LOGIN_URL = "https://1.tongji.edu.cn/api/ssoservice/system/loginIn";
    private static final long REQUEST_DELAY_MS = 100;

    public static User getUserFromPassword(String username, String password) {
        try (final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            final HttpRequest baseRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(LOGIN_URL))
                    .header("referer", "https://1.tongji.edu.cn/ssologin")
                    .GET()
                    .build();

            final var baseResponse = client.send(baseRequest, HttpResponse.BodyHandlers.ofString());

            final String baseRedirect = baseResponse.headers().map().get("location").getFirst();
            final String baseSetCookie = baseResponse.headers().map().get("set-cookie").getFirst();

            final String baseState = baseRedirect.split("state=")[1].split("&")[0];
            final String baseClientId = baseRedirect.split("client_id=")[1].split("&")[0];
            final String baseRedirectUri = baseRedirect.split("redirect_uri=")[1].split("&")[0];

            final String baseJsessionid = baseSetCookie.split("JSESSIONID=")[1].split(";")[0];

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseRedirect))
                    .header("referer", "https://1.tongji.edu.cn/")
                    .GET()
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            final var setCookies = response.headers().map().get("set-cookie");

            final var redirectUrl = response.headers().map().get("location").getFirst();
            final var authnLcKey = redirectUrl.split("authnLcKey=")[1].split("&")[0];
            final var entityID = redirectUrl.split("entityId=")[1].split("&")[0];

            String session = null;
            for (String setCookie : setCookies) {
                if(setCookie.contains("SESSION=")) {
                    session = setCookie.split("SESSION=")[1].split(";")[0];
                }
            }

            if(session == null) {
                throw new RuntimeException("Failed to extract cookies");
            }

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest finalRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(redirectUrl))
                    .header("referer", "https://1.tongji.edu.cn/")
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session)
                    .GET()
                    .build();

            client.send(finalRequest, HttpResponse.BodyHandlers.discarding());

            Thread.sleep(REQUEST_DELAY_MS);

            final String CHAIN_URL = "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=" + entityID + "&authnLcKey=" + authnLcKey;
            final HttpRequest chainRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(CHAIN_URL))
                    .header("referer", "https://1.tongji.edu.cn/")
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session)
                    .GET()
                    .build();

            final var chainResponse = client.send(chainRequest, HttpResponse.BodyHandlers.ofString());
            final int i = chainResponse.body().indexOf("name=\"spAuthChainCode\" id=\"spAuthChainCode24\"");
            final String spAuthChainCode = chainResponse.body().substring(i).split("value=\"")[1].split("\"")[0];

            Thread.sleep(REQUEST_DELAY_MS);

            final String LOGIN_URL = "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?authnLcKey=" + authnLcKey;

            Map<Object, Object> formData = Map.of(
                    "j_username", username,
                    "j_password", RSAUtil.encrypt(password, RSAUtil.RSA_PUBLIC_KEY),
                    "j_checkcode", "%E8%AF%B7%E8%BE%93%E5%85%A5%E9%AA%8C%E8%AF%81%E7%A0%81",
                    "op", "login",
                    "spAuthChainCode", spAuthChainCode,
                    "authnLcKey", authnLcKey
            );

            // Convert the map to a URL-encoded string
            String formBody = formData.entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8) + "=" +
                            URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            final HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(LOGIN_URL))
                    .header("referer", "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=" + entityID + "&authnLcKey=" + authnLcKey)
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session + "; x=x")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            final var loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest loginRequestSecond = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://iam.tongji.edu.cn/idp/AuthnEngine?currentAuth=urn_oasis_names_tc_SAML_2.0_ac_classes_BAMUsernamePassword&authnLcKey=" + authnLcKey + "&entityId=" + entityID))
                    .header("referer", "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=" + entityID + "&authnLcKey=" + authnLcKey)
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session + "; x=x")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            final var loginResponseSecond = client.send(loginRequestSecond, HttpResponse.BodyHandlers.ofString());
            final String idpSession = loginResponseSecond.headers().map().get("set-cookie").getFirst().split("_idp_session=")[1].split(";")[0];
            final String loginRedirect = loginResponseSecond.headers().map().get("location").getFirst();

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest jesessidRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://iam.tongji.edu.cn/idp/profile/OAUTH2/AuthorizationCode/SSO?authnLcKey=" + authnLcKey + "&entityId=" + entityID))
                    .header("referer", "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=" + entityID + "&authnLcKey=" + authnLcKey)
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session + "; _idp_session=" + idpSession + "; x=x")
                    .GET()
                    .build();

            final var jsessidResponse = client.send(jesessidRequest, HttpResponse.BodyHandlers.ofString());
            final String jsessidRedirect = jsessidResponse.headers().map().get("location").getFirst();

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest finalJessidRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(jsessidRedirect))
                    .header("referer", "https://iam.tongji.edu.cn/")
                    .header("cookie", "JSESSIONID=" + baseJsessionid)
                    .GET()
                    .build();

            final var finalJsessidResponse = client.send(finalJessidRequest, HttpResponse.BodyHandlers.ofString());
            String finalJsesId = null;
            for (String s : finalJsessidResponse.headers().map().get("set-cookie")) {
                if(s.contains("JSESSIONID=")) {
                    finalJsesId = s.split("JSESSIONID=")[1].split(";")[0];
                }
            }
            final String tokenRedirect = finalJsessidResponse.headers().map().get("location").getFirst();
            final String token = tokenRedirect.split("token=")[1].split("&")[0];
            final String uid = tokenRedirect.split("uid=")[1].split("&")[0];
            final String ts = tokenRedirect.split("ts=")[1].split("&")[0];

            if(finalJsesId == null) {
                throw new RuntimeException("Failed to extract final JSESSIONID");
            }

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest ssidRedirectRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(tokenRedirect))
                    .header("cookie", "JSESSIONID=" + finalJsesId)
                    .GET()
                    .build();

            var ssidRedirectResponse = client.send(ssidRedirectRequest, HttpResponse.BodyHandlers.ofString());
            final String ssidRedirect = ssidRedirectResponse.headers().map().get("location").getFirst();

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest ssidRedirectRequestSecond = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ssidRedirect))
                    .header("cookie", "JSESSIONID=" + finalJsesId)
                    .GET()
                    .build();

            var ssidRedirectResponseSecond = client.send(ssidRedirectRequestSecond, HttpResponse.BodyHandlers.ofString());

            Thread.sleep(REQUEST_DELAY_MS);

            final HttpRequest finalSsidRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://1.tongji.edu.cn/api/sessionservice/session/login"))
//                    .header("referer", tokenRedirect)
                    .header("origin", "https://1.tongji.edu.cn")
                    .header("priority", "u=1, i")
                    .header("cookie", "JSESSIONID=" + finalJsesId)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"uid\":\"" + uid + "\",\"token\":\"" + token +  "\",\"ts\":\"" + ts + "\"}"))
                    .build();

            final var finalSsidResponse = client.send(finalSsidRequest, HttpResponse.BodyHandlers.ofString());
            final String finalSessionId = finalSsidResponse.headers().map().get("set-cookie").getFirst().split("sessionid=")[1].split(";")[0];

            return getUserFromCookie("JSESSIONID=" + finalJsesId + "; sessionid=" + finalSessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static User getUserFromCookie(String cookie) {
        try (final HttpClient client = HttpClient.newBuilder().build()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(USER_INFO_URL))
                    .header("Cookie", cookie)
                    .GET()
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            final JSONObject data = JSONObject.parseObject(response.body()).getJSONObject("data");

            return new User(data, cookie);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Round> getRounds(User user) {
        try (final HttpClient client = HttpClient.newBuilder().build()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ROUNDS_URL))
                    .header("Cookie", user.getCookie())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return JSONArray.parseArray(JSONObject.parseObject(response.body()).getJSONArray("data").toJSONString(), Round.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
