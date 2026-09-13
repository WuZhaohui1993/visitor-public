import Cookies from "js-cookie";
import { useUserStoreHook } from "@/store/modules/user";
import { storageLocal, isString, isIncludeAllChildren } from "@pureadmin/utils";

export interface DataInfo<T> {
  accessToken?: string;
  expires?: T;
  refreshToken?: string;
  avatar?: string;
  username?: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
  mustChangePassword?: boolean;
}

export const userKey = "visitor-admin-user";
export const multipleTabsKey = "visitor-admin-session";

let memoryToken: DataInfo<number> | null = null;

export function getToken(): DataInfo<number> | null {
  return memoryToken;
}

export function setToken(data: DataInfo<Date>) {
  const expires = new Date(data.expires).getTime();
  memoryToken = {
    accessToken: data.accessToken,
    expires
  };

  Cookies.set(multipleTabsKey, "true");
  const profile = {
    avatar: data.avatar ?? "",
    username: data.username ?? "",
    nickname: data.nickname ?? "",
    roles: data.roles ?? [],
    permissions: data.permissions ?? []
    ,mustChangePassword: data.mustChangePassword ?? false
  };
  storageLocal().setItem(userKey, profile);
  useUserStoreHook().SET_AVATAR(profile.avatar);
  useUserStoreHook().SET_USERNAME(profile.username);
  useUserStoreHook().SET_NICKNAME(profile.nickname);
  useUserStoreHook().SET_ROLES(profile.roles);
  useUserStoreHook().SET_PERMS(profile.permissions);
}

export function removeToken() {
  memoryToken = null;
  Cookies.remove(multipleTabsKey);
  storageLocal().removeItem(userKey);
}

export const formatToken = (token: string): string => `Bearer ${token}`;

export const hasPerms = (value: string | string[]): boolean => {
  if (!value) return false;
  const { permissions } = useUserStoreHook();
  if (!permissions) return false;
  return isString(value)
    ? permissions.includes(value)
    : isIncludeAllChildren(value, permissions);
};
