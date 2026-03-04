package xyz.zcraft.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.User;
import xyz.zcraft.elect.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class NetworkHelper {
    private static final Logger LOG = org.apache.logging.log4j.LogManager.getLogger(NetworkHelper.class);
    private static final long REQUEST_DELAY_MS = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public static User getUserFromPassword(String username, String password) {
        try (final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TIMEOUT)
                .build()) {
            LOG.info("Starting login process for user {}", username);
            LOG.info("Logging in...(1/11) - Getting login entry point");
            final String LOGIN_ENTRY_URL = "https://1.tongji.edu.cn/api/ssoservice/system/loginIn";
            final HttpRequest baseRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(LOGIN_ENTRY_URL))
                    .header("referer", "https://1.tongji.edu.cn/ssologin")
                    .GET()
                    .build();

            final var baseResponse = client.send(baseRequest, HttpResponse.BodyHandlers.ofString());

            final String baseRedirect = baseResponse.headers().map().get("location").getFirst();
            final String baseSetCookie = baseResponse.headers().map().get("set-cookie").getFirst();

//            final String baseState = baseRedirect.split("state=")[1].split("&")[0];
//            final String baseClientId = baseRedirect.split("client_id=")[1].split("&")[0];
//            final String baseRedirectUri = baseRedirect.split("redirect_uri=")[1].split("&")[0];

            final String baseJsessionid = baseSetCookie.split("JSESSIONID=")[1].split(";")[0];

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(2/11) - Redirecting to SSO login page");
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
                if (setCookie.contains("SESSION=")) {
                    session = setCookie.split("SESSION=")[1].split(";")[0];
                    break;
                }
            }

            if (session == null) {
                throw new RuntimeException("Failed to extract cookies");
            }

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(3/11) - Accessing SSO login page");
            final HttpRequest finalRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(redirectUrl))
                    .header("referer", "https://1.tongji.edu.cn/")
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session)
                    .GET()
                    .build();

            client.send(finalRequest, HttpResponse.BodyHandlers.discarding());

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(4/11) - Getting authentication chain code");
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

            final String LOGIN_URL = "https://iam.tongji.edu.cn:443/idp/authcenter/ActionAuthChain?authnLcKey=" + authnLcKey;

            Map<Object, Object> formData = Map.of(
                    "j_username", username,
                    "j_password", RSAUtil.encrypt(password, RSAUtil.RSA_PUBLIC_KEY),
                    "j_checkcode", "%E8%AF%B7%E8%BE%93%E5%85%A5%E9%AA%8C%E8%AF%81%E7%A0%81", //todo captcha?
                    "op", "login",
                    "spAuthChainCode", spAuthChainCode,
                    "authnLcKey", authnLcKey
            );

            // Convert the map to a URL-encoded string
            String formBody = formData.entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8) + "=" +
                            URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            LOG.info("Logging in...(5/11) - Submitting login form");
            final HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(LOGIN_URL))
                    .header("referer", CHAIN_URL)
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session + "; x=x")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            final var loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            if (Objects.equals(JSONObject.parseObject(loginResponse.body()).getString("loginFailed"), "true")) {
                throw new RuntimeException("Login failed: " + JSONObject.parseObject(loginResponse.body()).getString("message"));
            }

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(6/11) - Following login redirect");
            final HttpRequest loginRequestSecond = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://iam.tongji.edu.cn:443/idp/AuthnEngine?currentAuth=urn_oasis_names_tc_SAML_2.0_ac_classes_BAMUsernamePassword&authnLcKey=" + authnLcKey + "&entityId=" + entityID))
                    .header("referer", "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=" + entityID + "&authnLcKey=" + authnLcKey)
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session + "; x=x")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            final var loginResponseSecond = client.send(loginRequestSecond, HttpResponse.BodyHandlers.ofString());
            final String idpSession = loginResponseSecond.headers().map().get("set-cookie").getFirst().split("_idp_session=")[1].split(";")[0];
//            final String loginRedirect = loginResponseSecond.headers().map().get("location").getFirst();

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(7/11) - Accessing post-login redirect");
            final HttpRequest jesessidRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://iam.tongji.edu.cn/idp/profile/OAUTH2/AuthorizationCode/SSO?authnLcKey=" + authnLcKey + "&entityId=" + entityID))
                    .header("referer", "https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=" + entityID + "&authnLcKey=" + authnLcKey)
                    .header("cookie", "_idp_authn_lc_key=" + authnLcKey + "; SESSION=" + session + "; _idp_session=" + idpSession + "; x=x")
                    .GET()
                    .build();

            final var jsessidResponse = client.send(jesessidRequest, HttpResponse.BodyHandlers.ofString());
            final String jsessidRedirect = jsessidResponse.headers().map().get("location").getFirst();

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(8/11) - Following JSESSIONID redirect");
            final HttpRequest finalJessidRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(jsessidRedirect))
                    .header("referer", "https://iam.tongji.edu.cn/")
                    .header("cookie", "JSESSIONID=" + baseJsessionid)
                    .GET()
                    .build();

            final var finalJsessidResponse = client.send(finalJessidRequest, HttpResponse.BodyHandlers.ofString());
            String finalJsesId = null;
            for (String s : finalJsessidResponse.headers().map().get("set-cookie")) {
                if (s.contains("JSESSIONID=")) {
                    finalJsesId = s.split("JSESSIONID=")[1].split(";")[0];
                    break;
                }
            }
            final String tokenRedirect = finalJsessidResponse.headers().map().get("location").getFirst();
            final String token = tokenRedirect.split("token=")[1].split("&")[0];
            final String uid = tokenRedirect.split("uid=")[1].split("&")[0];
            final String ts = tokenRedirect.split("ts=")[1].split("&")[0];

            if (finalJsesId == null) {
                throw new RuntimeException("Failed to extract final JSESSIONID");
            }

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(9/11) - Accessing token redirect");
            final HttpRequest ssidRedirectRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(tokenRedirect))
                    .header("cookie", "JSESSIONID=" + finalJsesId)
                    .GET()
                    .build();

            var ssidRedirectResponse = client.send(ssidRedirectRequest, HttpResponse.BodyHandlers.ofString());
            final String ssidRedirect = ssidRedirectResponse.headers().map().get("location").getFirst();

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(10/11) - Following token redirect");
            final HttpRequest ssidRedirectRequestSecond = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ssidRedirect))
                    .header("cookie", "JSESSIONID=" + finalJsesId)
                    .GET()
                    .build();

            client.send(ssidRedirectRequestSecond, HttpResponse.BodyHandlers.discarding());

            Thread.sleep(REQUEST_DELAY_MS);

            LOG.info("Logging in...(11/11) - Finalizing login and getting session cookie");
            final HttpRequest finalSsidRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://1.tongji.edu.cn/api/sessionservice/session/login"))
//                    .header("referer", tokenRedirect)
                    .header("origin", "https://1.tongji.edu.cn")
                    .header("priority", "u=1, i")
                    .header("cookie", "JSESSIONID=" + finalJsesId)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"uid\":\"" + uid + "\",\"token\":\"" + token + "\",\"ts\":\"" + ts + "\"}"))
                    .build();

            final var finalSsidResponse = client.send(finalSsidRequest, HttpResponse.BodyHandlers.ofString());
            final String finalSessionId = finalSsidResponse.headers().map().get("set-cookie").getFirst().split("sessionid=")[1].split(";")[0];

            LOG.info("Login successful for user {}", username);
            return getUserFromCookie("JSESSIONID=" + finalJsesId + "; sessionid=" + finalSessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static User getUserFromCookie(String cookie) {
        LOG.info("Getting user info from cookie");
        final String USER_INFO_URL = "https://1.tongji.edu.cn/api/sessionservice/session/getSessionUser";
        try (final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(USER_INFO_URL))
                    .header("Cookie", cookie)
                    .GET()
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            final JSONObject data = JSONObject.parseObject(response.body()).getJSONObject("data");
            final User user = new User(data, cookie);
            LOG.info("User {} info retrieved successfully", user.getUid());
            return user;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<RoundData> getRounds(User user) {
        final String ROUNDS_URL = "https://1.tongji.edu.cn/api/electionservice/student/getRounds?projectId=1";
        try (final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ROUNDS_URL))
                    .header("Cookie", user.getCookie())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return JSONArray.parseArray(JSONObject.parseObject(response.body()).getJSONArray("data").toJSONString(), RoundData.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Course> getRoundCourses(User user, int roundId) {
        final String COURSES_URL = "https://1.tongji.edu.cn/api/electionservice/student/" + roundId + "/getDataBk";
        try (final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(COURSES_URL))
                    .header("Cookie", user.getCookie())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            final JSONObject coursesObject = JSONObject.parseObject(response.body()).getJSONObject("data");

            final LinkedList<Course> courseList = new LinkedList<>();

            final LinkedList<String> roundCourseIds = new LinkedList<>();
            final JSONArray roundCoursesArray = coursesObject.getJSONArray("roundCourses");
            for (int i = 0; i < roundCoursesArray.size(); i++) {
                roundCourseIds.add(roundCoursesArray.getString(i));
            }

            final LinkedList<String> completedCourseIds = new LinkedList<>();
            final JSONArray completedCoursesArray = coursesObject.getJSONArray("completedCourses");
            for (int i = 0; i < completedCoursesArray.size(); i++) {
                completedCourseIds.add(completedCoursesArray.getJSONObject(i).getJSONObject("course").getString("courseCode"));
            }

            final JSONArray planCourses = coursesObject.getJSONArray("planCourses");
            for (int i = 0; i < planCourses.size(); i++) {
                final JSONObject courseObject = planCourses.getJSONObject(i).getJSONObject("course");
                Course course = new Course(Course.Type.PLAN, -1
                        , courseObject.to(CourseData.class), null);

                if (roundCourseIds.contains(course.getCourseData().courseCode())) {
                    course.setCompleteStatus(completedCourseIds.contains(course.getCourseData().courseCode()) ? 1 :0);
                    courseList.add(course);
                }
            }

            final JSONArray publicCourses = coursesObject.getJSONArray("publicCourses");
            for (int i = 0; i < publicCourses.size(); i++) {
                final JSONObject courseObject = publicCourses.getJSONObject(i);
                final JSONObject courseDataObject = courseObject.getJSONObject("course");
                Course course = new Course(Course.Type.PUBLIC, -1
                        , courseDataObject.to(CourseData.class), courseObject.getString("tag"));

                if (roundCourseIds.contains(course.getCourseData().courseCode())){
                    course.setCompleteStatus(completedCourseIds.contains(course.getCourseData().courseCode()) ? 1 :0);
                    courseList.add(course);
                }
            }

            return courseList;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<TeachClass> getTeachClasses(User user, Round round, String courseCode) {
        final String TEACH_CLASSES_URL = "https://1.tongji.edu.cn/api/electionservice/student/getTeachClass4Limit?roundId=" + round.getRoundData().id() +
                "&courseCode=" + courseCode + "&studentId=" + user.getUid() +
                "&calendarId=" + round.getRoundData().calendarId() + "&showCourseCode=false";
        try (final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(TEACH_CLASSES_URL))
                    .header("Cookie", user.getCookie())
                    .header("Referer", "https://1.tongji.edu.cn/studentElect")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return JSONArray.parseArray(JSONObject.parseObject(response.body()).getJSONArray("data").toJSONString(), TeachClass.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
