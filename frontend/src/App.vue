<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  ShieldCheck,
  LogOut,
  Plus,
  RefreshCw,
  X,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  commands,
  labels,
  errors,
  taskActions,
  date,
} from "./schema.js";
const me = ref(null),
  login = ref({ username: "", password: "" }),
  page = ref("workbench"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  notice = ref(""),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  run = ref(null),
  dialog = ref(null),
  options = ref({
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  roles = ref([]),
  permissions = ref([]),
  stats = ref({ plans: {}, runs: {}, modes: {} }),
  work = ref({ plans: [], tasks: [] });
const can = (p) => me.value?.permissions.includes(p),
  record = computed(() => detail.value?.record),
  runRecord = computed(() => run.value?.record),
  title = computed(
    () =>
      me.value?.menus.find((m) => m.code === page.value)?.name || "切换工作台",
  );
const department = (id) =>
  options.value.departments.find((d) => d.id === id)?.name || "#" + id;
const f = (key, type = "text", extra = {}) => ({ key, type, ...extra }),
  choices = (list, label = "name", value = "id") =>
    list.map((a) => ({ label: a[label], value: a[value] }));
const candidates = (perm) =>
  options.value.accounts.filter((a) => a.permissions.includes(perm));
const editable = computed(
  () =>
    record.value?.status === "DRAFT" &&
    record.value.authorId === me.value?.id &&
    can("plan.write"),
);
const director = computed(
  () => record.value?.directorId === me.value?.id && can("plan.direct"),
);
const fieldLabel = (key, kind = page.value) =>
  key === "scope" && kind === "roles" ? "数据范围" : labels[key] || key;
const filtered = computed(() =>
  rows.value.filter((r) =>
    JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
  ),
);
const visibleRows = computed(() =>
  page.value === "plans"
    ? rows.value
    : filtered.value.slice(offset.value * 20, offset.value * 20 + 20),
);
const pageTotal = computed(() =>
  page.value === "plans" ? total.value : filtered.value.length,
);
const clone = (v) => JSON.parse(JSON.stringify(v));
const fields = {
  plans: () => [
    f("code", "text", { readonly: !!dialog.value?.id, max: 60 }),
    f("title", "text", { max: 160 }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
      readonly: !!dialog.value?.id,
    }),
    f("category", "select", {
      options: choices(
        options.value.dictionaries.filter((d) => d.type === "cutover"),
        "name",
        "code",
      ),
    }),
    f("directorId", "select", {
      options: choices(
        candidates("plan.direct").filter((a) => a.id !== me.value.id),
        "displayName",
      ),
    }),
    f("systemName", "text", { max: 160 }),
    f("scope", "textarea"),
    f("decisionCriteria", "textarea"),
    f("recoveryCriteria", "textarea"),
  ],
  steps: () => [
    f("title", "text", { max: 160 }),
    f("ownerId", "select", {
      options: choices(
        candidates("task.execute").filter(
          (a) => a.id !== record.value.directorId,
        ),
        "displayName",
      ),
    }),
    f("reviewerId", "select", {
      options: choices(
        candidates("task.verify").filter(
          (a) => a.id !== dialog.value?.values.ownerId,
        ),
        "displayName",
      ),
    }),
    f("minutes", "number", { min: 1, max: 1440 }),
    f("dependencies", "permissions", {
      options: choices(
        detail.value.steps.filter((s) => s.id !== dialog.value?.id),
        "title",
      ),
    }),
    f("instructions", "textarea"),
    f("verification", "textarea"),
    f("rollback", "textarea"),
  ],
  runs: () => [
    f("mode", "select", {
      options: [
        { value: "REHEARSAL", label: "演练" },
        { value: "LIVE", label: "正式切换" },
      ],
    }),
    f("reference", "text", { max: 100 }),
    f("deadline", "datetime-local"),
  ],
  users: () => [
    f("username"),
    f("displayName"),
    f("password", "password", { required: !dialog.value?.id }),
    f("roleId", "select", { options: choices(roles.value) }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
    }),
    f("enabled", "checkbox"),
  ],
  roles: () => [
    f("name"),
    f("scope", "select", {
      options: [
        { value: "ALL", label: "全部部门" },
        { value: "DEPARTMENT", label: "本部门" },
        { value: "SELF", label: "本人相关" },
      ],
    }),
    f("permissions", "permissions", {
      options: choices(permissions.value, "name", "code"),
    }),
  ],
  departments: () => [f("name")],
  menus: () => [
    f("code", "text", { readonly: true }),
    f("name"),
    f("nameEn"),
    f("permissionCode", "select", {
      options: choices(permissions.value, "name", "code"),
    }),
    f("position", "number"),
    f("enabled", "checkbox"),
  ],
  permissions: () => [f("code", "text", { readonly: true }), f("name")],
  dictionaries: () => [f("type"), f("code"), f("name"), f("nameEn")],
  settings: () => [f("code", "text", { readonly: true }), f("value")],
  password: () => [f("oldPassword", "password"), f("newPassword", "password")],
};
const dialogFields = computed(() =>
  dialog.value?.command
    ? [f("note", "textarea", { minLength: 10 })]
    : fields[dialog.value?.kind]?.() || [],
);
const adminColumns = computed(() =>
  page.value === "audit"
    ? ["actor", "action", "objectId", "createdAt"]
    : (fields[page.value]?.() || [])
        .map((f) => f.key)
        .filter((k) => k !== "password"),
);
function clear() {
  me.value = null;
  detail.value = null;
  run.value = null;
  dialog.value = null;
  rows.value = [];
  roles.value = [];
  permissions.value = [];
  options.value = {
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  };
  work.value = { plans: [], tasks: [] };
  stats.value = { plans: {}, runs: {}, modes: {} };
  page.value = "workbench";
}
function fail(e) {
  error.value = errors[e.message] || "操作未完成，请核对输入后重试";
  if (e.message === "UNAUTHENTICATED") clear();
}
/** 每次载入重新获取权限，并在忙碌时禁用切换和提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    options.value = await api("/options");
    if (can("admin") && me.value.scope === "ALL") {
      roles.value = await api("/admin/roles");
      permissions.value = await api("/admin/permissions");
    }
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "workbench";
    if (run.value) {
      run.value = await api("/runs/" + runRecord.value.id);
      return;
    }
    if (detail.value) {
      detail.value = await api("/plans/" + record.value.id);
      return;
    }
    if (page.value === "plans") {
      const v = await api(
        "/plans?" +
          new URLSearchParams({
            search: search.value,
            status: status.value,
            page: offset.value,
            size: 20,
            sort: sort.value,
          }),
      );
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else
      rows.value = await api(
        page.value === "audit" ? "/audit" : "/admin/" + page.value,
      );
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  saving.value = true;
  try {
    await api("/auth/logout", "POST");
    clear();
    resetCsrf();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function navigate(code) {
  if (loading.value || saving.value) return;
  page.value = code;
  detail.value = null;
  run.value = null;
  offset.value = 0;
  search.value = "";
  status.value = "";
  notice.value = "";
  await load();
}
async function show(id, kind = "plan") {
  if (loading.value || saving.value) return;
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    if (kind === "run") {
      run.value = await api("/runs/" + id);
      detail.value = null;
    } else {
      detail.value = await api("/plans/" + id);
      run.value = null;
    }
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function back() {
  if (run.value) return show(run.value.plan.id);
  detail.value = null;
  await load();
}
function edit(kind, row) {
  const dt = new Date(
    Date.now() +
      Math.min(
        2,
        Number(
          options.value.settings.find((s) => s.code === "runWindowHours")
            ?.value,
        ) || 48,
      ) *
        3600000,
  );
  dialog.value = {
    kind,
    id: row?.id,
    title:
      (row ? "编辑" : "新建") +
      (kind === "steps" ? "任务" : kind === "runs" ? "运行" : title.value),
    requestKey: crypto.randomUUID(),
    values: row
      ? clone(row)
      : {
          enabled: true,
          permissions: [],
          dependencies: [],
          scope: kind === "roles" ? "DEPARTMENT" : "",
          departmentId: me.value.departmentId,
          category: "MIGRATION",
          type: "cutover",
          minutes: 15,
          mode: "REHEARSAL",
          deadline: new Date(dt - dt.getTimezoneOffset() * 60000)
            .toISOString()
            .slice(0, 16),
        },
  };
}
function command(action, task) {
  dialog.value = {
    kind: run.value ? "runs" : "plans",
    command: action,
    taskId: task?.record.id,
    title: commands[action],
    requestKey: crypto.randomUUID(),
    values: { note: "" },
  };
}
function remove(kind, row) {
  dialog.value = {
    kind,
    id: row?.id,
    title: "删除" + (row?.title || row?.name || row?.username || "草稿"),
    delete: true,
    values: {},
  };
}
/** 幂等UUID随窗口保留，截止时间转UTC；服务端拒绝旧版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function submitDialog() {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  const d = dialog.value;
  try {
    const v = clone(d.values);
    let path,
      method = "POST";
    if (d.kind === "password") {
      await api("/auth/password", "POST", v);
      clear();
      resetCsrf();
      notice.value = "密码已更新，请重新登录";
      return;
    }
    if (d.delete) {
      method = "DELETE";
      path =
        d.kind === "plans"
          ? "/plans/" + record.value.id + "?version=" + record.value.version
          : d.kind === "steps"
            ? "/plans/" +
              record.value.id +
              "/steps/" +
              d.id +
              "?version=" +
              record.value.version
            : "/admin/" + d.kind + "/" + d.id;
    } else if (d.command) {
      v.version = run.value ? runRecord.value.version : record.value.version;
      v.requestKey = d.requestKey;
      path =
        (run.value
          ? "/runs/" +
            runRecord.value.id +
            (d.taskId ? "/tasks/" + d.taskId : "")
          : "/plans/" + record.value.id) +
        "/commands/" +
        d.command;
    } else if (d.kind === "plans") {
      path = "/plans" + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
      v.version = d.id ? record.value.version : undefined;
      v.requestKey = d.requestKey;
    } else if (d.kind === "steps") {
      path = "/plans/" + record.value.id + "/steps" + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
      v.version = record.value.version;
      v.requestKey = d.requestKey;
    } else if (d.kind === "runs") {
      path = "/plans/" + record.value.id + "/runs";
      v.version = record.value.version;
      v.requestKey = d.requestKey;
      v.deadline = new Date(v.deadline).toISOString();
    } else {
      path = "/admin/" + d.kind + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
    }
    const result = await api(path, method, v);
    dialog.value = null;
    notice.value = "已保存";
    if (d.delete && d.kind === "plans") detail.value = null;
    if (d.kind === "runs" && !d.command) {
      run.value = result;
      detail.value = null;
    }
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function exportReport() {
  loading.value = true;
  try {
    const v = await api("/runs/" + runRecord.value.id + "/report.json");
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(v, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "run-" + runRecord.value.id + ".json";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function move(n) {
  offset.value += n;
  await load();
}
const display = (r, k) =>
  k === "roleId"
    ? roles.value.find((x) => x.id === r[k])?.name
    : k === "departmentId"
      ? department(r[k])
      : k === "permissions"
        ? r[k]
            ?.map((v) => permissions.value.find((p) => p.code === v)?.name || v)
            .join("、")
        : k === "enabled"
          ? r[k]
            ? "启用"
            : "停用"
          : k === "scope"
            ? { ALL: "全部部门", DEPARTMENT: "本部门", SELF: "本人相关" }[
                r[k]
              ] || r[k]
            : k === "createdAt"
              ? date(r[k])
              : r[k];
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" />
      <p class="eyebrow">CUTOVERFLOW / 0.1.0</p>
      <h1>系统切换与<br />回退演练台账</h1>
      <p>方案 · 演练 · 执行 · 恢复</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技官网 <ArrowUpRight :size="15"
      /></a>
    </section>
    <section class="login-form">
      <p class="eyebrow">CUTOVER OPERATIONS</p>
      <h2>登录切换工作台</h2>
      <form @submit.prevent="signIn">
        <label
          >账号<input
            v-model="login.username"
            autocomplete="username"
            required /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" class="success">{{ notice }}</p>
        <button class="primary" :disabled="saving">登录</button>
      </form>
      <p class="subtle">公开源码学习版 · 未经书面授权不得商用</p>
      <small
        >上海如静知华信息科技有限公司<br />商业咨询微信 zhuatech /
        zhuatech2</small
      >
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong>CutoverFlow</strong>
      </div>
      <p class="sidebar-caption">
        {{
          options.settings.find((s) => s.code === "companyName")?.value ||
          "系统切换协作"
        }}
      </p>
      <nav>
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: page === m.code }"
          :disabled="loading || saving"
          @click="navigate(m.code)"
        >
          <ShieldCheck :size="17" />{{ m.name }}
        </button>
      </nav>
      <footer class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 <ArrowUpRight :size="13" /></a
        ><small>公开源码学习版 0.1.0</small>
      </footer>
    </aside>
    <div class="content-shell">
      <header class="topbar">
        <span>{{ department(me.departmentId) }}</span>
        <div>
          <button
            :disabled="loading || saving"
            @click="
              dialog = { kind: 'password', title: '修改密码', values: {} }
            "
          >
            修改密码</button
          ><span>{{ me.displayName }} · {{ me.role }}</span
          ><button
            aria-label="退出登录"
            :disabled="loading || saving"
            @click="signOut"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <p class="eyebrow">CUTOVER OPERATIONS</p>
            <h1>{{ run ? "运行执行台" : detail ? "切换方案" : title }}</h1>
          </div>
          <button :disabled="loading || saving" @click="load">
            <RefreshCw :size="16" />刷新
          </button>
        </div>
        <p v-if="loading" class="subtle">正在读取记录…</p>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <div v-if="notice" role="status" class="success">{{ notice }}</div>
        <fieldset class="operations" :disabled="loading || saving">
          <template v-if="run"
            ><button @click="back"><ChevronLeft :size="16" />返回方案</button>
            <section class="panel">
              <div class="detail-head">
                <div>
                  <p class="eyebrow">
                    {{ run.plan.code }} /
                    {{ runRecord.mode === "LIVE" ? "正式切换" : "演练" }} #{{
                      runRecord.id
                    }}
                  </p>
                  <h2>{{ run.plan.title }}</h2>
                </div>
                <span class="badge" :class="runRecord.status">{{
                  states[runRecord.status]
                }}</span>
              </div>
              <p>
                {{ runRecord.reference }} · 截止 {{ date(runRecord.deadline) }}
              </p>
              <p v-if="runRecord.overdue" class="error">
                已到执行期限，请启动回退
              </p>
              <div class="toolbar">
                <template
                  v-if="me.id === run.plan.directorId && can('plan.direct')"
                  ><button
                    v-if="
                      runRecord.status === 'VERIFYING' && !runRecord.overdue
                    "
                    class="primary"
                    @click="command('accept')"
                  >
                    验收运行</button
                  ><button
                    v-if="
                      ['RUNNING', 'BLOCKED', 'VERIFYING'].includes(
                        runRecord.status,
                      )
                    "
                    class="danger"
                    @click="command('abort')"
                  >
                    启动回退</button
                  ><button
                    v-if="runRecord.status === 'ROLLING_BACK'"
                    class="primary"
                    @click="command('finish-rollback')"
                  >
                    确认恢复完成
                  </button></template
                ><button v-if="can('export')" @click="exportReport">
                  导出运行报告
                </button>
              </div>
              <dl class="detail-grid">
                <div>
                  <dt>继续 / 回退依据</dt>
                  <dd>{{ run.plan.decisionCriteria }}</dd>
                </div>
                <div>
                  <dt>恢复验收依据</dt>
                  <dd>{{ run.plan.recoveryCriteria }}</dd>
                </div>
                <div v-if="runRecord.decision">
                  <dt>指挥结论</dt>
                  <dd>{{ runRecord.decision }}</dd>
                </div>
              </dl>
            </section>
            <section class="panel">
              <h3>任务执行与独立核验</h3>
              <article
                v-for="t in run.tasks"
                :key="t.record.id"
                class="run-task"
              >
                <header>
                  <div>
                    <span class="task-number">#{{ t.step.id }}</span
                    ><strong>{{ t.step.title }}</strong
                    ><small
                      >执行 {{ t.step.ownerName }} · 核验
                      {{ t.step.reviewerName }} ·
                      {{ t.step.minutes }}分钟</small
                    >
                  </div>
                  <span class="badge" :class="t.record.status">{{
                    states[t.record.status]
                  }}</span>
                </header>
                <div class="dependency">
                  前置：{{
                    t.step.dependencies.map((id) => "#" + id).join("、") ||
                    "无"
                  }}<span v-if="t.record.rollbackStatus !== 'NONE'">
                    · 回退：{{ states[t.record.rollbackStatus] }}</span
                  >
                </div>
                <details>
                  <summary>步骤、标准与证据</summary>
                  <dl class="detail-grid">
                    <div>
                      <dt>执行步骤</dt>
                      <dd>{{ t.step.instructions }}</dd>
                    </div>
                    <div>
                      <dt>独立验收标准</dt>
                      <dd>{{ t.step.verification }}</dd>
                    </div>
                    <div>
                      <dt>回退步骤</dt>
                      <dd>{{ t.step.rollback }}</dd>
                    </div>
                    <div v-if="t.record.executionEvidence">
                      <dt>执行记录</dt>
                      <dd>{{ t.record.executionEvidence }}</dd>
                    </div>
                    <div v-if="t.record.reviewEvidence">
                      <dt>核验记录</dt>
                      <dd>{{ t.record.reviewEvidence }}</dd>
                    </div>
                    <div v-if="t.record.rollbackEvidence">
                      <dt>回退记录</dt>
                      <dd>{{ t.record.rollbackEvidence }}</dd>
                    </div>
                    <div v-if="t.record.rollbackReview">
                      <dt>回退核验</dt>
                      <dd>{{ t.record.rollbackReview }}</dd>
                    </div>
                  </dl>
                </details>
                <div class="toolbar">
                  <button
                    v-for="a in taskActions(runRecord, t, me, run.tasks)"
                    :key="a"
                    :class="{
                      primary: ['start', 'pass', 'rollback-pass'].includes(a),
                      danger: ['fail', 'reject'].includes(a),
                    }"
                    @click="command(a, t)"
                  >
                    {{ commands[a] }}
                  </button>
                </div>
              </article>
            </section>
            <section class="panel">
              <h3>运行事件</h3>
              <ol class="timeline">
                <li v-for="e in run.events" :key="e.id">
                  <strong>{{
                    commands[e.action.toLowerCase()] || e.action
                  }}</strong
                  ><span>人员 #{{ e.actorId }} · {{ date(e.createdAt) }}</span>
                  <p>{{ e.note }}</p>
                </li>
              </ol>
            </section></template
          >
          <template v-else-if="detail"
            ><button @click="back"><ChevronLeft :size="16" />返回列表</button>
            <section class="panel">
              <div class="detail-head">
                <div>
                  <p class="eyebrow">
                    {{ record.code }} / {{ record.systemName }}
                  </p>
                  <h2>{{ record.title }}</h2>
                </div>
                <span class="badge" :class="record.status">{{
                  states[record.status]
                }}</span>
              </div>
              <p>
                编制 {{ record.authorName }} · 指挥 {{ record.directorName }} ·
                {{ department(record.departmentId) }}
              </p>
              <div class="toolbar">
                <template v-if="editable"
                  ><button @click="edit('plans', record)">编辑方案</button
                  ><button @click="edit('steps')">
                    <Plus :size="16" />新增任务</button
                  ><button
                    v-if="!record.submitted"
                    @click="remove('plans', record)"
                  >
                    删除草稿</button
                  ><button class="primary" @click="command('submit')">
                    提交放行
                  </button></template
                ><template v-if="director"
                  ><template v-if="record.status === 'REVIEW'"
                    ><button class="primary" @click="command('approve')">
                      放行并冻结</button
                    ><button @click="command('return')">
                      退回编制
                    </button></template
                  ><template v-if="record.status === 'READY'"
                    ><button class="primary" @click="edit('runs')">
                      启动运行</button
                    ><button @click="command('retire')">
                      退役方案
                    </button></template
                  ></template
                >
              </div>
              <dl class="detail-grid">
                <div>
                  <dt>影响范围</dt>
                  <dd>{{ record.scope }}</dd>
                </div>
                <div>
                  <dt>继续 / 回退依据</dt>
                  <dd>{{ record.decisionCriteria }}</dd>
                </div>
                <div>
                  <dt>恢复验收依据</dt>
                  <dd>{{ record.recoveryCriteria }}</dd>
                </div>
              </dl>
            </section>
            <section class="panel">
              <h3>切换任务 {{ detail.steps.length }}</h3>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>任务</th>
                      <th>前置依赖</th>
                      <th>执行 / 核验</th>
                      <th>时长</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="s in detail.steps" :key="s.id">
                      <td>
                        <strong>#{{ s.id }} {{ s.title }}</strong>
                        <details>
                          <summary>步骤与验收标准</summary>
                          <p>{{ s.instructions }}</p>
                          <p>{{ s.verification }}</p>
                          <p>{{ s.rollback }}</p>
                        </details>
                      </td>
                      <td>
                        {{
                          s.dependencies.map((id) => "#" + id).join("、") ||
                          "无"
                        }}
                      </td>
                      <td>{{ s.ownerName }} / {{ s.reviewerName }}</td>
                      <td>{{ s.minutes }}分钟</td>
                      <td>
                        <template v-if="editable"
                          ><button @click="edit('steps', s)">编辑</button
                          ><button @click="remove('steps', s)">
                            删除
                          </button></template
                        >
                      </td>
                    </tr>
                    <tr v-if="!detail.steps.length">
                      <td colspan="5" class="empty">尚未编制任务</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
            <section class="panel">
              <h3>历次演练与切换</h3>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>运行</th>
                      <th>凭证</th>
                      <th>开始 / 截止</th>
                      <th>状态</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in detail.runs" :key="r.id">
                      <td>
                        #{{ r.id }}
                        {{ r.mode === "LIVE" ? "正式切换" : "演练" }}
                      </td>
                      <td>{{ r.reference }}</td>
                      <td>
                        {{ date(r.startedAt)
                        }}<small>{{ date(r.deadline) }}</small>
                      </td>
                      <td>
                        <span class="badge" :class="r.status">{{
                          states[r.status]
                        }}</span>
                      </td>
                      <td>
                        <button @click="show(r.id, 'run')">打开运行</button>
                      </td>
                    </tr>
                    <tr v-if="!detail.runs.length">
                      <td colspan="5" class="empty">暂无运行</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
            <section class="panel">
              <h3>方案事件</h3>
              <ol class="timeline">
                <li v-for="e in detail.events" :key="e.id">
                  <strong>{{
                    commands[e.action.toLowerCase()] || e.action
                  }}</strong
                  ><span>人员 #{{ e.actorId }} · {{ date(e.createdAt) }}</span>
                  <p>{{ e.note }}</p>
                </li>
              </ol>
            </section></template
          >
          <template v-else-if="page === 'workbench'"
            ><div class="work-grid">
              <section class="panel">
                <h3>我的方案与指挥</h3>
                <button
                  v-for="p in work.plans"
                  :key="p.id"
                  class="task-row"
                  @click="show(p.id)"
                >
                  <span>{{ p.code }} · {{ p.title }}</span
                  ><span class="badge" :class="p.status">{{
                    states[p.status]
                  }}</span>
                </button>
                <p v-if="!work.plans.length" class="empty">暂无待处理方案</p>
              </section>
              <section class="panel">
                <h3>我的执行与核验</h3>
                <button
                  v-for="t in work.tasks"
                  :key="t.task.record.id"
                  class="task-row"
                  @click="show(t.runId, 'run')"
                >
                  <span
                    >{{ t.task.step.title
                    }}<small
                      >{{ t.planCode }} ·
                      {{ t.mode === "LIVE" ? "正式切换" : "演练" }} #{{
                        t.runId
                      }}</small
                    ></span
                  ><span class="badge" :class="t.runStatus">{{
                    states[t.runStatus]
                  }}</span>
                </button>
                <p v-if="!work.tasks.length" class="empty">暂无未结任务</p>
              </section>
            </div></template
          >
          <template v-else-if="page === 'dashboard'"
            ><div class="metrics">
              <div class="metric">
                <span>方案总数</span
                ><strong>{{
                  Object.values(stats.plans).reduce((a, b) => a + b, 0)
                }}</strong>
              </div>
              <div class="metric">
                <span>演练运行</span
                ><strong>{{ stats.modes.REHEARSAL || 0 }}</strong>
              </div>
              <div class="metric">
                <span>正式切换</span
                ><strong>{{ stats.modes.LIVE || 0 }}</strong>
              </div>
              <div class="metric">
                <span>已核验任务 / 总任务</span
                ><strong
                  >{{ stats.verified || 0 }} /
                  {{ stats.taskCount || 0 }}</strong
                >
              </div>
            </div>
            <div class="work-grid">
              <section class="panel">
                <h3>方案状态</h3>
                <div v-for="(n, s) in stats.plans" :key="s" class="stat-row">
                  <span>{{ states[s] }}</span
                  ><strong>{{ n }}</strong>
                </div>
                <p v-if="!Object.keys(stats.plans).length" class="empty">
                  暂无方案
                </p>
              </section>
              <section class="panel">
                <h3>运行结果</h3>
                <div v-for="(n, s) in stats.runs" :key="s" class="stat-row">
                  <span>{{ states[s] }}</span
                  ><strong>{{ n }}</strong>
                </div>
                <p v-if="!Object.keys(stats.runs).length" class="empty">
                  暂无运行
                </p>
              </section>
            </div></template
          >
          <template v-else
            ><section class="panel">
              <form
                class="filters"
                @submit.prevent="
                  offset = 0;
                  load();
                "
              >
                <input
                  v-model="search"
                  aria-label="搜索记录"
                  placeholder="搜索编号或名称"
                /><template v-if="page === 'plans'"
                  ><select v-model="status" aria-label="状态筛选">
                    <option value="">全部状态</option>
                    <option
                      v-for="s in ['DRAFT', 'REVIEW', 'READY', 'RETIRED']"
                      :key="s"
                      :value="s"
                    >
                      {{ states[s] }}
                    </option></select
                  ><select v-model="sort" aria-label="排序">
                    <option value="newest">最近创建</option>
                    <option value="oldest">最早创建</option>
                    <option value="code">编号顺序</option>
                  </select></template
                ><button>查询</button
                ><button
                  v-if="
                    page === 'plans'
                      ? can('plan.write') && me.scope !== 'SELF'
                      : can('admin') &&
                        !['audit', 'menus', 'permissions', 'settings'].includes(
                          page,
                        )
                  "
                  class="primary"
                  type="button"
                  @click="edit(page)"
                >
                  <Plus :size="16" />新建
                </button>
              </form>
              <div class="table-wrap">
                <table v-if="page === 'plans'">
                  <thead>
                    <tr>
                      <th>编号 / 方案</th>
                      <th>系统</th>
                      <th>编制 / 指挥</th>
                      <th>状态</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="p in visibleRows" :key="p.id">
                      <td>
                        <strong>{{ p.code }}</strong
                        ><small>{{ p.title }}</small>
                      </td>
                      <td>{{ p.systemName }}</td>
                      <td>{{ p.authorName }} / {{ p.directorName }}</td>
                      <td>
                        <span class="badge" :class="p.status">{{
                          states[p.status]
                        }}</span>
                      </td>
                      <td><button @click="show(p.id)">查看方案</button></td>
                    </tr>
                  </tbody>
                </table>
                <table v-else>
                  <thead>
                    <tr>
                      <th v-for="k in adminColumns" :key="k">
                        {{
                          (k === "scope" && page === "roles"
                            ? "数据范围"
                            : labels[k]) ||
                          {
                            actor: "操作人",
                            action: "操作",
                            objectId: "对象",
                            createdAt: "时间",
                          }[k] ||
                          k
                        }}
                      </th>
                      <th v-if="page !== 'audit'">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in visibleRows" :key="r.id">
                      <td
                        v-for="k in adminColumns"
                        :key="k"
                        :class="{
                          'config-cell': [
                            'permissions',
                            'value',
                            'nameEn',
                          ].includes(k),
                        }"
                      >
                        {{ display(r, k) }}
                      </td>
                      <td v-if="page !== 'audit'">
                        <button @click="edit(page, r)">编辑</button
                        ><button
                          v-if="
                            !['menus', 'permissions', 'settings'].includes(page)
                          "
                          @click="remove(page, r)"
                        >
                          删除
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!visibleRows.length" class="empty">暂无记录</p>
              </div>
              <div class="pagination">
                <span>共 {{ pageTotal }} 条 · 第 {{ offset + 1 }} 页</span
                ><button
                  aria-label="上一页"
                  :disabled="offset === 0"
                  @click="move(-1)"
                >
                  <ChevronLeft :size="16" /></button
                ><button
                  aria-label="下一页"
                  :disabled="(offset + 1) * 20 >= pageTotal"
                  @click="move(1)"
                >
                  <ChevronRight :size="16" />
                </button>
              </div></section
          ></template>
        </fieldset>
      </main>
    </div>
  </div>
  <div v-if="dialog" class="overlay" @click.self="!saving && (dialog = null)">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.title"
    >
      <header>
        <h2>{{ dialog.title }}</h2>
        <button aria-label="关闭" @click="dialog = null" :disabled="saving">
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="submitDialog">
        <p v-if="dialog.delete" class="subtle">
          确认删除这条记录？已引用或留有提交历史的记录将被拒绝。
        </p>
        <div class="form-grid">
          <component
            :is="field.type === 'permissions' ? 'div' : 'label'"
            v-for="field in dialog.delete ? [] : dialogFields"
            :key="field.key"
            :class="{
              full: field.type === 'textarea' || field.type === 'permissions',
            }"
            ><span>{{ fieldLabel(field.key, dialog.kind) }}</span
            ><select
              v-if="field.type === 'select'"
              v-model="dialog.values[field.key]"
              :disabled="field.readonly"
              required
            >
              <option :value="undefined">请选择</option>
              <option
                v-for="o in field.options"
                :key="o.value"
                :value="o.value"
              >
                {{ o.label }}
              </option></select
            ><textarea
              v-else-if="field.type === 'textarea'"
              v-model="dialog.values[field.key]"
              :maxlength="field.max || 2000"
              :minlength="field.minLength || 1"
              required
              rows="3"
            ></textarea>
            <div v-else-if="field.type === 'permissions'" class="checks">
              <label v-for="o in field.options" :key="o.value"
                ><input
                  v-model="dialog.values[field.key]"
                  type="checkbox"
                  :value="o.value"
                />{{ o.label }}</label
              >
            </div>
            <input
              v-else-if="field.type === 'checkbox'"
              v-model="dialog.values[field.key]"
              type="checkbox" /><input
              v-else
              v-model="dialog.values[field.key]"
              :type="field.type"
              :min="field.min"
              :max="field.max"
              :maxlength="field.max || 200"
              :readonly="field.readonly"
              :required="field.required !== false"
              :autocomplete="field.type === 'password' ? 'new-password' : 'off'"
              :step="field.type === 'number' ? 1 : undefined"
          /></component>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" @click="dialog = null" :disabled="saving">
            取消</button
          ><button class="primary" :disabled="saving || loading">
            {{ saving ? "正在保存…" : "确认保存" }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
