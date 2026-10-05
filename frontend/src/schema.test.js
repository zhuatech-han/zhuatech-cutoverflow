// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { taskActions } from "./schema.js";
const owner = { id: 2, permissions: ["task.execute"] },
  reviewer = { id: 3, permissions: ["task.verify"] };
const t = (id, status, deps = [], rollbackStatus = "NONE") => ({
  record: { status, rollbackStatus },
  step: { id, ownerId: 2, reviewerId: 3, dependencies: deps },
});
test("predecessor needs independent verification", () => {
  const a = t(1, "VERIFYING"),
    b = t(2, "PENDING", [1]);
  assert.deepEqual(taskActions({ status: "RUNNING" }, b, owner, [a, b]), []);
  a.record.status = "DONE";
  assert.deepEqual(taskActions({ status: "RUNNING" }, b, owner, [a, b]), [
    "start",
  ]);
});
test("owner cannot verify own work", () => {
  const a = t(1, "VERIFYING");
  assert.deepEqual(taskActions({ status: "RUNNING" }, a, owner, [a]), []);
  assert.deepEqual(taskActions({ status: "RUNNING" }, a, reviewer, [a]), [
    "pass",
    "reject",
  ]);
});
test("failure blocks all forward progress", () => {
  const a = t(1, "RUNNING");
  assert.deepEqual(taskActions({ status: "BLOCKED" }, a, owner, [a]), []);
});
test("overdue execution can record failure but cannot complete", () => {
  const a = t(1, "RUNNING");
  assert.deepEqual(
    taskActions({ status: "RUNNING", overdue: true }, a, owner, [a]),
    ["fail"],
  );
});
test("rollback waits for successor independent review", () => {
  const a = t(1, "DONE", [], "PENDING"),
    b = t(2, "FAILED", [1], "VERIFYING");
  assert.deepEqual(
    taskActions({ status: "ROLLING_BACK" }, a, owner, [a, b]),
    [],
  );
  b.record.rollbackStatus = "DONE";
  assert.deepEqual(taskActions({ status: "ROLLING_BACK" }, a, owner, [a, b]), [
    "rollback",
  ]);
});
test("rollback reviewer can pass or reject while owner cannot self attest", () => {
  const a = t(1, "FAILED", [], "VERIFYING");
  assert.deepEqual(taskActions({ status: "ROLLING_BACK" }, a, reviewer, [a]), [
    "rollback-pass",
    "rollback-reject",
  ]);
  assert.deepEqual(taskActions({ status: "ROLLING_BACK" }, a, owner, [a]), []);
});
test("terminal run and revoked permission have no actions", () => {
  const a = t(1, "PENDING");
  assert.deepEqual(taskActions({ status: "ACCEPTED" }, a, owner, [a]), []);
  assert.deepEqual(
    taskActions({ status: "RUNNING" }, a, { id: 2, permissions: [] }, [a]),
    [],
  );
});
