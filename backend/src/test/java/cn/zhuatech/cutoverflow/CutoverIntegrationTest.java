// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cutoverflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真正HTTP接口的依赖、独立核验、冻结、演练、补偿、并发与权限验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CutoverIntegrationTest.TimeConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CutoverIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("cutoverflow.admin-password", () -> password);
  }

  /** 可控期限测试时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    volatile Instant value = Instant.parse("2026-10-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  /** 覆盖业务时钟，仅用于测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, author, director, owner, reviewer, other, outside;
  long dep, authorId, directorId, ownerId, reviewerId, ownerRole, plan, step1, step2;
  String suffix;

  @BeforeAll
  void users() throws Exception {
    admin = login("admin");
    suffix = UUID.randomUUID().toString().substring(0, 8);
    dep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 切换-" + suffix))
            .path("id")
            .asLong();
    long otherDep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 另一部门-" + suffix))
            .path("id")
            .asLong();
    var roles = new HashMap<String, Long>();
    for (var x : ok(admin, "GET", "/admin/roles", null))
      roles.put(x.path("name").asString(), x.path("id").asLong());
    authorId = user("author", roles.get("方案编制员"), dep);
    directorId = user("director", roles.get("切换指挥员"), dep);
    ownerRole = roles.get("任务执行员");
    ownerId = user("owner", ownerRole, dep);
    reviewerId = user("reviewer", roles.get("任务核验员"), dep);
    user("other", ownerRole, dep);
    user("outside", roles.get("方案编制员"), otherDep);
    author = login("author-" + suffix);
    director = login("director-" + suffix);
    owner = login("owner-" + suffix);
    reviewer = login("reviewer-" + suffix);
    other = login("other-" + suffix);
    outside = login("outside-" + suffix);
  }

  @BeforeEach
  void setup() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00Z");
    plan = ok(author, "POST", "/plans", input()).path("id").asLong();
    addStep("核对迁移备份", Set.of());
    step1 = detail().path("steps").get(0).path("id").asLong();
    addStep("切换业务入口", Set.of(step1));
    step2 = detail().path("steps").get(1).path("id").asLong();
  }

  Map<String, Object> input() {
    var m = new HashMap<String, Object>();
    m.put("requestKey", key());
    m.put("code", "TEST-" + key().substring(0, 8));
    m.put("title", "TEST 跨团队切换验证");
    m.put("departmentId", dep);
    m.put("directorId", directorId);
    m.put("category", "MIGRATION");
    m.put("systemName", "TEST 学习业务系统");
    m.put("scope", "TEST 隔离环境，未连接生产设施");
    m.put("decisionCriteria", "TEST 所有指定任务独立核验完成");
    m.put("recoveryCriteria", "TEST 入口恢复且核对虚构数据一致性");
    return m;
  }

  Map<String, Object> stepInput(String title, Set<Long> deps) throws Exception {
    return new HashMap<>(
        Map.of(
            "version",
            pv(),
            "requestKey",
            key(),
            "title",
            title,
            "ownerId",
            ownerId,
            "reviewerId",
            reviewerId,
            "minutes",
            15,
            "instructions",
            "TEST 根据受控步骤执行并登记实际证据",
            "verification",
            "TEST 独立检查结果与验收依据一致",
            "rollback",
            "TEST 恢复原入口并独立核对结果",
            "dependencies",
            deps));
  }

  void addStep(String title, Set<Long> deps) throws Exception {
    ok(author, "POST", "/plans/" + plan + "/steps", stepInput(title, deps));
  }

  JsonNode detail() throws Exception {
    return ok(author, "GET", "/plans/" + plan, null);
  }

  long pv() throws Exception {
    return detail().path("record").path("version").asLong();
  }

  Map<String, Object> pc() throws Exception {
    return Map.of("version", pv(), "requestKey", key(), "note", "TEST 经独立核对任务与恢复依据后批准");
  }

  void approve() throws Exception {
    ok(author, "POST", "/plans/" + plan + "/commands/submit", pc());
    ok(director, "POST", "/plans/" + plan + "/commands/approve", pc());
  }

  Map<String, Object> ri(String mode) throws Exception {
    return Map.of(
        "version",
        pv(),
        "requestKey",
        key(),
        "mode",
        mode,
        "reference",
        "TEST-" + key(),
        "deadline",
        clock.instant().plusSeconds(7200));
  }

  long run(String mode) throws Exception {
    return ok(director, "POST", "/plans/" + plan + "/runs", ri(mode))
        .path("record")
        .path("id")
        .asLong();
  }

  JsonNode rd(long r) throws Exception {
    return ok(director, "GET", "/runs/" + r, null);
  }

  long task(long r, int n) throws Exception {
    return rd(r).path("tasks").get(n).path("record").path("id").asLong();
  }

  Map<String, Object> rc(long r) throws Exception {
    return Map.of(
        "version",
        rd(r).path("record").path("version").asLong(),
        "requestKey",
        key(),
        "note",
        "TEST 已实际核对执行或恢复证据编号");
  }

  JsonNode tc(MockHttpSession who, long r, int n, String action) throws Exception {
    return ok(who, "POST", tp(r, n, action), rc(r));
  }

  String tp(long r, int n, String a) throws Exception {
    return "/runs/" + r + "/tasks/" + task(r, n) + "/commands/" + a;
  }

  void completed(long r, int n) throws Exception {
    tc(owner, r, n, "start");
    tc(owner, r, n, "complete");
    tc(reviewer, r, n, "pass");
  }

  void accepted(long r) throws Exception {
    completed(r, 0);
    completed(r, 1);
    ok(director, "POST", "/runs/" + r + "/commands/accept", rc(r));
  }

  @Test
  void rehearsalThenLiveAndIndependentTasks() throws Exception {
    approve();
    long r = run("REHEARSAL");
    expect(owner, "POST", tp(r, 1, "start"), rc(r), 409, "DEPENDENCY_BLOCKED");
    expect(admin, "POST", tp(r, 0, "start"), rc(r), 403, "NOT_ASSIGNED");
    expect(other, "POST", tp(r, 0, "start"), rc(r), 403, "OUT_OF_SCOPE");
    accepted(r);
    assertEquals("ACCEPTED", rd(r).path("record").path("status").asString());
    long live = run("LIVE");
    assertEquals("PENDING", rd(live).path("tasks").get(0).path("record").path("status").asString());
    accepted(live);
  }

  @Test
  void failureReverseRollbackPreservesEvidence() throws Exception {
    approve();
    long r = run("REHEARSAL");
    completed(r, 0);
    tc(owner, r, 1, "start");
    tc(owner, r, 1, "fail");
    expect(owner, "POST", tp(r, 0, "start"), rc(r), 409, "INVALID_STATE");
    ok(director, "POST", "/runs/" + r + "/commands/abort", rc(r));
    expect(owner, "POST", tp(r, 0, "rollback"), rc(r), 409, "ROLLBACK_ORDER");
    tc(owner, r, 1, "rollback");
    tc(reviewer, r, 1, "rollback-pass");
    tc(owner, r, 0, "rollback");
    expect(
        director,
        "POST",
        "/runs/" + r + "/commands/finish-rollback",
        rc(r),
        409,
        "INCOMPLETE_ROLLBACK");
    tc(reviewer, r, 0, "rollback-pass");
    ok(director, "POST", "/runs/" + r + "/commands/finish-rollback", rc(r));
    assertEquals("ROLLED_BACK", rd(r).path("record").path("status").asString());
    assertEquals("FAILED", rd(r).path("tasks").get(1).path("record").path("status").asString());
    assertFalse(
        rd(r).path("tasks").get(1).path("record").path("executionEvidence").asString().isBlank());
  }

  @Test
  void cyclesAndReferencedDelete() throws Exception {
    expect(
        author,
        "PUT",
        "/plans/" + plan + "/steps/" + step1,
        stepInput("循环任务", Set.of(step2)),
        400,
        "CYCLIC_DEPENDENCY");
    expect(
        author,
        "POST",
        "/plans/" + plan + "/steps",
        stepInput("不存在前置", Set.of(999999L)),
        400,
        "INVALID_DEPENDENCY");
    expect(
        author,
        "DELETE",
        "/plans/" + plan + "/steps/" + step1 + "?version=" + pv(),
        null,
        409,
        "DEPENDENCY_REFERENCED");
    ok(author, "DELETE", "/plans/" + plan + "/steps/" + step2 + "?version=" + pv(), null);
    assertEquals(1, detail().path("steps").size());
  }

  @Test
  void scopeListDetailExportAndAdmin() throws Exception {
    expect(outside, "GET", "/plans/" + plan, null, 403, "OUT_OF_SCOPE");
    expect(other, "GET", "/plans/" + plan, null, 403, "OUT_OF_SCOPE");
    assertEquals(0, ok(other, "GET", "/plans", null).path("total").asInt());
    expect(owner, "GET", "/admin/users", null, 403, "FORBIDDEN");
    approve();
    long r = run("REHEARSAL");
    expect(outside, "GET", "/runs/" + r + "/report.json", null, 403, "OUT_OF_SCOPE");
    assertEquals(
        plan,
        ok(owner, "GET", "/runs/" + r + "/report.json", null).path("plan").path("id").asLong());
  }

  @Test
  void liveNeedsAcceptedRehearsalAndSingleActiveRun() throws Exception {
    approve();
    expect(director, "POST", "/plans/" + plan + "/runs", ri("LIVE"), 409, "REHEARSAL_REQUIRED");
    run("REHEARSAL");
    expect(director, "POST", "/plans/" + plan + "/runs", ri("REHEARSAL"), 409, "ACTIVE_RUN");
  }

  @Test
  void frozenAndStale() throws Exception {
    var stale = stepInput("旧任务", Set.of());
    addStep("增加并行任务", Set.of());
    expect(author, "POST", "/plans/" + plan + "/steps", stale, 409, "STALE_VERSION");
    approve();
    expect(
        author,
        "POST",
        "/plans/" + plan + "/steps",
        stepInput("冻结后新任务", Set.of()),
        409,
        "INVALID_STATE");
    expect(admin, "POST", "/plans/" + plan + "/commands/retire", pc(), 403, "NOT_ASSIGNED");
  }

  @Test
  void exactReplayAndChangedPayloadConflict() throws Exception {
    var in = input();
    var first = ok(author, "POST", "/plans", in);
    var replay = ok(author, "POST", "/plans", in);
    assertEquals(first.path("id").asLong(), replay.path("id").asLong());
    in.put("title", "TEST 同键更换不可接受内容");
    expect(author, "POST", "/plans", in, 409, "IDEMPOTENCY_CONFLICT");
  }

  @Test
  void concurrentReplayOneEventAndOneRevision() throws Exception {
    approve();
    long r = run("REHEARSAL");
    String path = tp(r, 0, "start");
    var input = rc(r);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var fs =
          pool.invokeAll(
              List.<Callable<Integer>>of(
                  () -> status(owner, "POST", path, input),
                  () -> status(owner, "POST", path, input)));
      for (var f : fs) assertEquals(200, f.get());
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, rd(r).path("record").path("version").asLong());
    long count = 0;
    for (var e : rd(r).path("events")) if (e.path("action").asString().equals("START")) count++;
    assertEquals(1, count);
  }

  @Test
  void deadlineStopsExecutionButAllowsRecovery() throws Exception {
    approve();
    long r = run("REHEARSAL");
    clock.value = clock.instant().plusSeconds(7200);
    expect(owner, "POST", tp(r, 0, "start"), rc(r), 409, "DEADLINE_REACHED");
    ok(director, "POST", "/runs/" + r + "/commands/abort", rc(r));
    ok(director, "POST", "/runs/" + r + "/commands/finish-rollback", rc(r));
  }

  @Test
  void independentOwnersAndDirector() throws Exception {
    var combined =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "TEST 双职责-" + key(),
                    "scope",
                    "SELF",
                    "permissions",
                    Set.of("plan.read", "task.execute", "task.verify")))
            .path("id")
            .asLong();
    long dual = user("dual", combined, dep);
    var st = stepInput("不允许自己核验", Set.of());
    st.put("ownerId", dual);
    st.put("reviewerId", dual);
    expect(author, "POST", "/plans/" + plan + "/steps", st, 400, "INDEPENDENCE_REQUIRED");
    var same = input();
    same.put("directorId", ok(admin, "GET", "/auth/me", null).path("id").asLong());
    expect(admin, "POST", "/plans", same, 400, "INDEPENDENCE_REQUIRED");
  }

  @Test
  void rollbackRejectionMustBeRedone() throws Exception {
    approve();
    long r = run("REHEARSAL");
    tc(owner, r, 0, "start");
    ok(director, "POST", "/runs/" + r + "/commands/abort", rc(r));
    tc(owner, r, 0, "rollback");
    tc(reviewer, r, 0, "rollback-reject");
    expect(
        director,
        "POST",
        "/runs/" + r + "/commands/finish-rollback",
        rc(r),
        409,
        "INCOMPLETE_ROLLBACK");
    tc(owner, r, 0, "rollback");
    tc(reviewer, r, 0, "rollback-pass");
    ok(director, "POST", "/runs/" + r + "/commands/finish-rollback", rc(r));
  }

  @Test
  void submittedHistoryCannotBeDeleted() throws Exception {
    ok(author, "POST", "/plans/" + plan + "/commands/submit", pc());
    ok(director, "POST", "/plans/" + plan + "/commands/return", pc());
    expect(author, "DELETE", "/plans/" + plan + "?version=" + pv(), null, 409, "HISTORY_PROTECTED");
  }

  @Test
  void csrfPasswordAndLastAdminProtection() throws Exception {
    var request =
        post("/api/plans")
            .session(author)
            .contentType("application/json")
            .content(json.writeValueAsString(input()));
    assertEquals(403, mvc.perform(request).andReturn().getResponse().getStatus());
    expect(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            "weak-" + key(),
            "displayName",
            "TEST 弱密码",
            "departmentId",
            dep,
            "roleId",
            ownerRole,
            "enabled",
            true,
            "password",
            "short"),
        400,
        "WEAK_PASSWORD");
    long adminId = ok(admin, "GET", "/auth/me", null).path("id").asLong();
    expect(admin, "DELETE", "/admin/users/" + adminId, null, 409, "LAST_ADMIN");
  }

  @Test
  void roleRevocationAppliesToOldSession() throws Exception {
    long id = user("revoked", ownerRole, dep);
    var session = login("revoked-" + suffix);
    var roles = ok(admin, "GET", "/admin/roles", null);
    long reader = 0;
    for (var role : roles)
      if (role.path("name").asString().equals("方案编制员")) reader = role.path("id").asLong();
    ok(
        admin,
        "PUT",
        "/admin/users/" + id,
        Map.of(
            "username",
            "revoked-" + suffix,
            "displayName",
            "TEST revoked",
            "departmentId",
            dep,
            "roleId",
            reader,
            "enabled",
            true));
    expect(
        session,
        "POST",
        "/runs/999999/tasks/1/commands/start",
        Map.of("version", 0, "requestKey", key(), "note", "TEST 即时权限复核阻断"),
        403,
        "FORBIDDEN");
  }

  long user(String n, long role, long department) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                n + "-" + suffix,
                "displayName",
                "TEST " + n,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                department,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  MockHttpSession login(String name) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession();
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  MvcResult request(MockHttpSession who, String method, String path, Object body) throws Exception {
    var builder =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    builder.session(who).with(csrf());
    if (body != null)
      builder.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(builder).andReturn();
  }

  int status(MockHttpSession s, String m, String p, Object b) throws Exception {
    return request(s, m, p, b).getResponse().getStatus();
  }

  JsonNode ok(MockHttpSession s, String m, String p, Object b) throws Exception {
    var r = request(s, m, p, b);
    assertEquals(200, r.getResponse().getStatus(), p + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void expect(MockHttpSession s, String m, String p, Object b, int status, String code)
      throws Exception {
    var r = request(s, m, p, b);
    assertEquals(status, r.getResponse().getStatus(), p);
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }
}
