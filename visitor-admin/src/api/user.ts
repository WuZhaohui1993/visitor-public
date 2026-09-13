import { http } from "@/utils/http";

type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string;
};

type AdminProfile = {
  userId: number;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
};

type BackendLogin = {
  accessToken: string;
  expiresInSeconds: number;
  user: AdminProfile;
};

export type CaptchaResult = {
  required: boolean;
  challengeId?: string;
  imageData?: string;
};

export type UserResult = {
  code: number;
  message: string;
  data: {
    avatar: string;
    username: string;
    nickname: string;
    roles: string[];
    permissions: string[];
    accessToken: string;
    refreshToken: string;
    expires: Date;
    mustChangePassword: boolean;
  };
};

export type RefreshTokenResult = UserResult;

function adapt(response: ApiResponse<BackendLogin>): UserResult {
  if (!response.success) {
    return { code: 1, message: response.message, data: null } as UserResult;
  }
  const { user } = response.data;
  return {
    code: 0,
    message: response.message,
    data: {
      avatar: "",
      username: user.username,
      nickname: user.displayName,
      roles: user.roles,
      permissions: user.permissions,
      mustChangePassword: user.mustChangePassword,
      accessToken: response.data.accessToken,
      refreshToken: "",
      expires: new Date(Date.now() + response.data.expiresInSeconds * 1000)
    }
  };
}

export async function getLogin(data: object) {
  return adapt(
    await http.request<ApiResponse<BackendLogin>>("post", "/auth/login", {
      data,
      withCredentials: true
    })
  );
}

export async function getLoginCaptcha(username: string) {
  return (
    await http.get<ApiResponse<CaptchaResult>, never>("/auth/captcha", {
      params: { username }
    })
  ).data;
}

export async function refreshTokenApi() {
  return adapt(
    await http.request<ApiResponse<BackendLogin>>("post", "/auth/refresh", {
      withCredentials: true
    })
  );
}

export function logoutApi() {
  return http.request<ApiResponse<void>>("post", "/auth/logout", {
    withCredentials: true
  });
}
