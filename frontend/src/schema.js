// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: "草稿",
  REVIEW: "待放行",
  READY: "已冻结",
  RETIRED: "已退役",
  RUNNING: "执行中",
  BLOCKED: "失败阻断",
  VERIFYING: "待核验",
  ROLLING_BACK: "回退中",
  ACCEPTED: "已验收",
  ROLLED_BACK: "已恢复",
  PENDING: "待执行",
  DONE: "已核验",
  FAILED: "失败",
  NONE: "无需回退",
};
export const commands = {
  save_plan: "维护方案",
  save_step: "维护任务",
  delete_step: "删除任务",
  start_run: "启动运行",
  submit: "提交放行",
  approve: "放行并冻结",
  return: "退回编制",
  retire: "退役方案",
  start: "开始任务",
  complete: "提交执行证据",
  pass: "核验通过",
  reject: "核验失败",
  fail: "登记执行失败",
  abort: "启动回退",
  accept: "验收运行",
  "finish-rollback": "确认恢复完成",
  rollback: "登记回退证据",
  "rollback-pass": "回退核验通过",
  "rollback-reject": "退回回退证据",
  delete: "删除草稿",
};
export const labels = {
  code: "方案编号",
  title: "名称",
  category: "切换类别",
  departmentId: "归属部门",
  directorId: "切换指挥人",
  systemName: "系统或应用",
  scope: "影响范围与边界",
  decisionCriteria: "继续或回退的判定依据",
  recoveryCriteria: "恢复验收依据",
  ownerId: "执行人",
  reviewerId: "独立核验人",
  minutes: "预计时长（分钟）",
  instructions: "执行步骤",
  verification: "验收标准与证据要求",
  rollback: "回退步骤与数据处理",
  dependencies: "前置任务",
  mode: "运行类型",
  reference: "变更或演练凭证编号",
  deadline: "执行截止时间",
  note: "实际证据编号与核对结论",
  username: "账号",
  displayName: "显示名称",
  password: "初始或重置密码",
  roleId: "角色",
  enabled: "启用",
  name: "名称",
  nameEn: "英文名称",
  permissions: "业务权限",
  permissionCode: "所需权限",
  position: "排序",
  type: "字典类型",
  value: "参数值",
  oldPassword: "原密码",
  newPassword: "新密码",
};
export const errors = {
  UNAUTHENTICATED: "登录已失效，请重新登录",
  FORBIDDEN: "没有这项操作权限",
  OUT_OF_SCOPE: "记录超出当前数据范围",
  NOT_ASSIGNED: "仅指定人员可操作",
  INDEPENDENCE_REQUIRED: "执行人与核验人须独立；指挥人不能兼任编制或执行",
  STALE_VERSION: "记录已变化，请刷新后重新操作",
  IDEMPOTENCY_CONFLICT: "同一重试标识的内容已改变，请核对后重新操作",
  INVALID_STATE: "当前状态不允许这项操作",
  DEPENDENCY_BLOCKED: "前置任务尚未独立核验完成",
  ROLLBACK_ORDER: "请先完成已触达后继任务的回退核验",
  CYCLIC_DEPENDENCY: "任务依赖形成循环",
  INVALID_DEPENDENCY: "前置任务不存在、属于其他方案或引用自身",
  DEPENDENCY_REFERENCED: "此任务仍被其他任务依赖",
  REHEARSAL_REQUIRED: "须先完成同一冻结方案的演练并验收",
  ACTIVE_RUN: "当前方案已有未结运行",
  INCOMPLETE_RUN: "任务尚未全部独立核验",
  INCOMPLETE_ROLLBACK: "回退任务尚未全部独立核验",
  INVALID_DEADLINE: "截止时间须晚于现在且不超过48小时",
  DEADLINE_REACHED: "已到执行期限，请由指挥人启动回退",
  NO_STEPS: "请先编制至少一项任务",
  EVIDENCE_REQUIRED: "请填写至少10字的实际依据",
  INELIGIBLE_ACCOUNT: "人员未启用、权限或部门不符",
  IMMUTABLE_IDENTITY: "编号与归属部门不能更改",
  HISTORY_PROTECTED: "提交历史须保留，不能删除",
  INVALID_REQUEST_KEY: "请求标识无效，请重新打开窗口",
  INVALID_INPUT: "请检查必填内容和长度",
  INVALID_CODE: "编号须为3至60位字母、数字、点、下划线或连字符",
  INVALID_CATEGORY: "请选择有效切换类别",
  INVALID_STATUS: "状态筛选无效",
  WEAK_PASSWORD: "密码至少12位，含大小写字母与数字，最多72字节",
  LAST_ADMIN: "必须保留可用的全范围管理员",
  CONFLICT: "编号重复或资源已有引用",
  BUILTIN_RESOURCE: "内建资源不能删除或更换标识",
  NETWORK_ERROR: "连接未完成，请检查服务后重试",
  DIRECTORY_LIMIT: "管理目录超过10000条上限",
  REPORT_LIMIT: "统计超过1000方案上限",
  STEP_LIMIT: "每份方案最多100项任务",
  RUN_LIMIT: "每份方案最多200次运行",
  LOGIN_FAILED: "账号或密码不正确",
  LOGIN_THROTTLED: "登录失败较多，请稍后重试",
  NOT_FOUND: "记录不存在",
};
/** 根据当前服务端状态和指派显示动作；接口再次独立校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function taskActions(run, task, me, tasks) {
  if (!run || !me) return [];
  const t = task.record,
    s = task.step,
    out = [];
  const can = (p) => me.permissions.includes(p);
  if (run.status === "ROLLING_BACK") {
    if (
      t.rollbackStatus === "PENDING" &&
      s.ownerId === me.id &&
      can("task.execute") &&
      !tasks.some(
        (x) =>
          !["NONE", "DONE"].includes(x.record.rollbackStatus) &&
          x.step.dependencies.includes(s.id),
      )
    )
      out.push("rollback");
    if (
      t.rollbackStatus === "VERIFYING" &&
      s.reviewerId === me.id &&
      can("task.verify")
    )
      out.push("rollback-pass", "rollback-reject");
    return out;
  }
  if (run.status !== "RUNNING") return out;
  if (s.ownerId === me.id && can("task.execute")) {
    if (
      t.status === "PENDING" &&
      !run.overdue &&
      s.dependencies.every(
        (id) => tasks.find((x) => x.step.id === id)?.record.status === "DONE",
      )
    )
      out.push("start");
    if (t.status === "RUNNING") {
      if (!run.overdue) out.push("complete");
      out.push("fail");
    }
  }
  if (
    t.status === "VERIFYING" &&
    s.reviewerId === me.id &&
    can("task.verify")
  ) {
    if (!run.overdue) out.push("pass");
    out.push("reject");
  }
  return out;
}
/** 所有时间以上海时区显示，保存时转UTC。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const date = (v) =>
  v
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "Asia/Shanghai",
      }).format(new Date(v))
    : "—";
