package org.sunbird.user;

import static akka.testkit.JavaTestKit.duration;
import static org.junit.Assert.assertTrue;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.Constants;
import org.sunbird.common.ElasticSearchUtil;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.user.actors.UserManagementActor;
import scala.concurrent.duration.FiniteDuration;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  ElasticSearchUtil.class,
  CassandraOperationImpl.class,
  DataCacheHandler.class,
  org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.class
})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*"})
@SuppressStaticInitializationFor("org.sunbird.common.ElasticSearchUtil")
public class UserAssignRoleTest {

  private static final FiniteDuration ACTOR_MAX_WAIT_DURATION = duration("120 second");
  private static String ID = "id001";
  private static String orgId = "testOrg001";
  private static String userId = "testUser001";
  private static String externalId = "testExternal001";
  private static String provider = "testProvider001";
  private static String hashtagId = "hashTagId001";
  private static List<String> ALL_ROLES =
      Arrays.asList(
          "CONTENT_CREATOR",
          "COURSE_MENTOR",
          "BOOK_CREATOR",
          "BOOK_REVIEWER",
          "OFFICIAL_TEXTBOOK_BADGE_ISSUER",
          "TEACHER_BADGE_ISSUER",
          "ANNOUNCEMENT_SENDER",
          "CONTENT_REVIEWER",
          "FLAG_REVIEWER",
          "PUBLIC");
  private static Map<String, Object> userOrg = new HashMap<>();

  private static ActorSystem system;
  private static Props props;
  private static CassandraOperation cassandraOperation = null;
  private static Response response = null;
  private static Map<String, Object> esRespone = new HashMap<>();

  @BeforeClass
  public static void setUp() throws Exception {
    system = ActorSystem.create("system");
    props = Props.create(UserManagementActor.class);

    userOrg.put(JsonKey.ID, ID);
    userOrg.put(JsonKey.ORGANISATION_ID, orgId);
    userOrg.put(JsonKey.USER_ID, userId);
    userOrg.put(JsonKey.HASHTAGID, hashtagId);
    userOrg.put(JsonKey.EXTERNAL_ID, externalId);
    userOrg.put(JsonKey.PROVIDER, provider);
    userOrg.put(
        JsonKey.ROLES,
        Arrays.asList(
            "CONTENT_CREATOR", "COURSE_MENTOR", "BOOK_CREATOR", "BOOK_REVIEWER", "PUBLIC"));

    response = new Response();
    Map<String, Object> responseMap = new HashMap<>();

    responseMap.put(Constants.RESPONSE, Arrays.asList(userOrg));
    response.getResult().putAll(responseMap);

    esRespone.put(JsonKey.CONTENT, Arrays.asList(userOrg));
  }

  @Before
  public void mockClasses() throws Exception {

    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperation = PowerMockito.mock(CassandraOperationImpl.class);
    PowerMockito.when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);

    Map<String, Object> roleMap = new HashMap<>();
    for (String role : ALL_ROLES) roleMap.put(role, role);
    PowerMockito.mockStatic(DataCacheHandler.class);
    PowerMockito.when(DataCacheHandler.getRoleMap()).thenReturn(roleMap);

    PowerMockito.mockStatic(ElasticSearchUtil.class);
    PowerMockito.when(
            ElasticSearchUtil.getDataByIdentifier(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(userOrg);
    PowerMockito.when(ElasticSearchUtil.complexSearch(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(esRespone);
  }

  private static void initCassandraForSuccess() {
    PowerMockito.when(
            cassandraOperation.getRecordsByProperties(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(response);

    Response updateResponse = new Response();
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(Constants.RESPONSE, Constants.SUCCESS);
    updateResponse.getResult().putAll(responseMap);

    PowerMockito.when(cassandraOperation.updateRecord(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(updateResponse);
  }

  @Test
  public void testAssignInvalidRolesFailure() throws Exception {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ASSIGN_ROLES.getValue());
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(JsonKey.USER_ID, userId);
    request.put(JsonKey.ORGANISATION_ID, orgId);
    request.put(JsonKey.ROLES, new ArrayList<>(Arrays.asList("DUMMY_ROLE")));
    reqObj.setRequest(request);

    initCassandraForSuccess();
    subject.tell(reqObj, probe.getRef());
    ProjectCommonException ex =
        probe.expectMsgClass(ACTOR_MAX_WAIT_DURATION, ProjectCommonException.class);
    assertTrue(null != ex);
  }

  @Test
  public void testAssignValidRolesSuccess() throws Exception {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ASSIGN_ROLES.getValue());
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(JsonKey.USER_ID, userId);
    request.put(JsonKey.ORGANISATION_ID, orgId);
    List<String> roles = new ArrayList<>(Arrays.asList("CONTENT_CREATOR", "COURSE_MENTOR"));
    request.put(JsonKey.ROLES, roles);
    reqObj.setRequest(request);

    initCassandraForSuccess();
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(ACTOR_MAX_WAIT_DURATION, Response.class);
    assertTrue(null != res);
  }

  @Test
  public void testAssignEmptyRoleSuccess() throws Exception {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ASSIGN_ROLES.getValue());
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(JsonKey.USER_ID, userId);
    request.put(JsonKey.ORGANISATION_ID, orgId);
    List<String> roles = new ArrayList<>();
    request.put(JsonKey.ROLES, roles);
    reqObj.setRequest(request);

    initCassandraForSuccess();
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(ACTOR_MAX_WAIT_DURATION, Response.class);
    assertTrue(null != res);
  }

  @Test
  public void testAssignValidRolesSuccessWithoutOrgID() throws Exception {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ASSIGN_ROLES.getValue());
    Map<String, Object> request = new HashMap<String, Object>();
    request.put(JsonKey.USER_ID, userId);
    request.put(JsonKey.EXTERNAL_ID, externalId);
    request.put(JsonKey.PROVIDER, provider);
    List<String> roles = new ArrayList<>(Arrays.asList("CONTENT_CREATOR", "COURSE_MENTOR"));
    request.put(JsonKey.ROLES, roles);
    reqObj.setRequest(request);

    initCassandraForSuccess();
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(ACTOR_MAX_WAIT_DURATION, Response.class);
    assertTrue(null != res);
  }
}
