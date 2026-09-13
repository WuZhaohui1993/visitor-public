import { http } from "@/utils/http";

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string;
};

export type PageResp<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DashboardSummary = {
  todayTotal: number;
  pending: number;
  approvedToday: number;
  rejectedToday: number;
  activeVisitors: number;
  integrationFailures: number;
  trend: Array<{ date: string; total: number; approved: number; rejected: number }>;
};

export type VisitorRecord = {
  bizId: string;
  recordNo: string;
  visitorName: string;
  idCardNo: string;
  phone: string;
  visitedUserId: string;
  visitedUserName: string;
  visitedDeptName: string;
  visitReason: string;
  plannedEntryTime: string;
  plannedExitTime: string;
  status: number;
  statusText: string;
  hikSyncStatus?: string;
  hikSyncError?: string;
  hikSyncTime?: string;
  hikRetryCount: number;
  hikAccessStatus?: string;
  hikAccessError?: string;
  hikAccessSyncTime?: string;
  hikAccessRetryCount: number;
  approveRemark?: string;
  approveTime?: string;
  createTime: string;
  expired: boolean;
  hasIdCardFront: boolean;
  hasIdCardBack: boolean;
  hasFacePhoto: boolean;
};

export type AuditLog = {
  id: number;
  actorUsername: string;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  summary: string;
  ipAddress: string;
  createTime: string;
};

export type IntegrationStatus = {
  dingtalk: IntegrationItem;
  hikvision: IntegrationItem;
  accessControl: IntegrationItem;
  storage: { provider: string; bucket: string; configured: boolean };
  cachedAccessTargets: number;
};

export type IntegrationItem = {
  enabled: boolean;
  configured: boolean;
  mode: string;
  endpoint: string;
};

export type AccessTarget = {
  resourceIndexCode: string;
  channelNo: number;
  sourceIndexCode: string;
  sourceName: string;
  sourceResourceType: string;
};

export type AdminUser = {
  id: number;
  username: string;
  displayName: string;
  enabled: boolean;
  mustChangePassword: boolean;
  roleCodes: string[];
  lockedUntil?: string;
  lastLoginTime?: string;
  createTime: string;
};

export type AdminRole = {
  id: number;
  code: string;
  name: string;
  enabled: boolean;
  builtIn: boolean;
  permissionCodes: string[];
  createTime: string;
  updateTime: string;
};

export type AdminPermission = {
  id: number;
  code: string;
  name: string;
  type: string;
  routePath?: string;
  sortOrder: number;
};

export type IntegrationConfig = {
  dingtalk: { enabled: boolean; mockMode: boolean; appKey: string; appSecret: string; corpId: string; agentId?: number; rootDeptId?: number };
  hikvision: {
    enabled: boolean;
    baseUrl: string;
    appKey: string;
    appSecret: string;
    tagId: string;
    userId: string;
    orgIndexCode: string;
    personAppId: string;
    faceGroupIndexCode: string;
    faceScoreEnabled: boolean;
    connectTimeoutSeconds: number;
    readTimeoutSeconds: number;
    trustAll: boolean;
    access: {
      enabled: boolean;
      resourceType: string;
      resourceIndexCodes: string[];
      autoDiscoverResources: boolean;
      resourceQueryPath: string;
      resourceQueryType: string;
      resourceQueryPageSize: number;
      channelNos: number[];
    };
  };
  storage: {
    provider: string;
    bucket: string;
    localRoot: string;
    previewBaseUrl: string;
    minio: { endpoint: string; accessKey: string; secretKey: string };
  };
};

export type IntegrationConfigVersion = {
  id: number;
  versionNo: number;
  status: string;
  testStatus: string;
  testSummary?: string;
  testedTime?: string;
  createdBy: number;
  submittedBy?: number;
  reviewedBy?: number;
  submittedTime?: string;
  publishedTime?: string;
  createTime: string;
};

export async function fetchDashboard() {
  return (await http.get<ApiResponse<DashboardSummary>, never>("/dashboard/summary")).data;
}

export async function fetchRecords(params: Record<string, unknown>) {
  return (await http.get<ApiResponse<PageResp<VisitorRecord>>, never>("/records", { params })).data;
}

export async function exportRecords(params: Record<string, unknown>) {
  return (
    await http.get<ApiResponse<VisitorRecord[]>, never>("/records/export", {
      params
    })
  ).data;
}

export async function fetchRecord(bizId: string) {
  return (await http.get<ApiResponse<VisitorRecord>, never>(`/records/${bizId}`)).data;
}

export function fetchRecordAttachment(bizId: string, type: string) {
  return http.request<Blob>("get", `/records/${bizId}/attachments/${type}`, {
    responseType: "blob"
  });
}

export async function runRecordOperation(bizId: string, operation: "hikvision-retry" | "access-retry" | "revoke") {
  return (
    await http.post<ApiResponse<VisitorRecord>, never>(
      `/records/${bizId}/operations/${operation}`,
      { headers: { "Idempotency-Key": crypto.randomUUID() } }
    )
  ).data;
}

export async function fetchAuditLogs(params: Record<string, unknown>) {
  return (await http.get<ApiResponse<PageResp<AuditLog>>, never>("/audit-logs", { params })).data;
}

export async function fetchIntegrationStatus() {
  return (await http.get<ApiResponse<IntegrationStatus>, never>("/integrations/status")).data;
}

export async function fetchAccessTargets() {
  return (await http.get<ApiResponse<AccessTarget[]>, never>("/integrations/hikvision/access-targets")).data;
}

export function refreshAccessTargets() {
  return http.post<ApiResponse<void>, never>("/integrations/hikvision/access-targets/refresh");
}

export function changeAdminPassword(currentPassword: string, newPassword: string) {
  return http.post<ApiResponse<void>, { currentPassword: string; newPassword: string }>("/auth/password", {
    data: { currentPassword, newPassword }
  });
}

export async function fetchAdminUsers() {
  return (await http.get<ApiResponse<AdminUser[]>, never>("/users")).data;
}

export async function fetchAdminRoles() {
  return (await http.get<ApiResponse<AdminRole[]>, never>("/roles")).data;
}

export async function fetchAdminPermissions() {
  return (await http.get<ApiResponse<AdminPermission[]>, never>("/permissions")).data;
}

export async function createAdminUser(data: { username: string; displayName: string; password: string; roleCodes: string[] }) {
  return (await http.post<ApiResponse<AdminUser>, typeof data>("/users", { data })).data;
}

export async function updateAdminUser(userId: number, data: { displayName: string; enabled: boolean; roleCodes: string[] }) {
  return (await http.request<ApiResponse<AdminUser>>("put", `/users/${userId}`, { data })).data;
}

export async function resetAdminPassword(userId: number, newPassword: string) {
  return (await http.post<ApiResponse<AdminUser>, { newPassword: string }>(`/users/${userId}/password-reset`, { data: { newPassword } })).data;
}

export async function createAdminRole(data: { code: string; name: string; enabled: boolean; permissionCodes: string[] }) {
  return (await http.post<ApiResponse<AdminRole>, typeof data>("/roles", { data })).data;
}

export async function updateAdminRole(roleId: number, data: { code: string; name: string; enabled: boolean; permissionCodes: string[] }) {
  return (await http.request<ApiResponse<AdminRole>>("put", `/roles/${roleId}`, { data })).data;
}

export async function fetchEffectiveConfig() {
  return (await http.get<ApiResponse<{ activeVersionId?: number; activeVersionNo?: number; config: IntegrationConfig }>, never>("/configs/effective")).data;
}

export async function fetchConfigVersions() {
  return (await http.get<ApiResponse<IntegrationConfigVersion[]>, never>("/configs/versions")).data;
}

export async function createConfigDraft(data: IntegrationConfig) {
  return (await http.post<ApiResponse<IntegrationConfigVersion>, IntegrationConfig>("/configs/drafts", { data })).data;
}

export async function runConfigAction(versionId: number, action: "test" | "submit" | "publish" | "rollback-draft") {
  return (await http.post<ApiResponse<IntegrationConfigVersion>, never>(`/configs/${versionId}/${action}`)).data;
}
