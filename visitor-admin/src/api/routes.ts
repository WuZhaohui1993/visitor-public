import { http } from "@/utils/http";

type Result = {
  code: number;
  message: string;
  data: Array<any>;
};

type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string;
};

export const getAsyncRoutes = async (): Promise<Result> => {
  const response = await http.get<ApiResponse<Array<any>>, never>(
    "/auth/routes"
  );
  return {
    code: response.success ? 0 : 1,
    message: response.message,
    data: response.data ?? []
  };
};
