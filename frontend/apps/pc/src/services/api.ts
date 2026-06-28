import {
  clearAccessToken,
  createApiClient,
  createRndApi,
  getAccessToken,
  setAccessToken,
  setStoredUser,
  type RndApi,
} from "@rnd/shared";

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler;
}

const client = createApiClient({
  getToken: getAccessToken,
  onUnauthorized: () => unauthorizedHandler?.(),
  onForbidden: () => unauthorizedHandler?.(),
});

export const api: RndApi = createRndApi(client);

export function persistLogin(accessToken: string, user: Parameters<typeof setStoredUser>[0]) {
  setAccessToken(accessToken);
  setStoredUser(user);
}

export function logout() {
  clearAccessToken();
}
