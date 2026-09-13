package com.visitor.system.visitor.service;

import com.visitor.system.visitor.dto.HikvisionAddFaceCommand;
import com.visitor.system.visitor.dto.HikvisionAddPersonCommand;
import com.visitor.system.visitor.dto.HikvisionFaceResult;
import com.visitor.system.visitor.dto.HikvisionGrantAccessCommand;
import com.visitor.system.visitor.dto.HikvisionRevokeAccessCommand;

/**
 * 海康业务客户端契约。
 *
 * <p>上层同步服务只关心“访客审批通过后能否入场、到期后能否退场”，
 * 这里把这些业务动作拆成海康侧必须完成的接口步骤：人员、人脸、门禁权限。
 */
public interface HikvisionClient {

    /**
     * 在 PMAS 中创建访客人员。
     *
     * <p>访客业务使用本系统生成的 personCode 作为海康 personIndexCode，
     * 便于后续补偿重试、重复登记和退场清理时能稳定定位同一个人。
     *
     * @return 海康返回的真实人员标识，优先用于后续人脸和权限下发
     */
    String addPerson(HikvisionAddPersonCommand command);

    /**
     * 查询已存在的海康人员标识。
     *
     * <p>用于处理“人员已存在”或新增人员后平台异步落库的情况：
     * 只要能查到真实 personId，就继续下发人脸和权限，不重复造人。
     */
    String findPersonId(String personCode);

    /**
     * 重新启用已存在人员。
     *
     * <p>同一身份证重复来访时可能复用旧人员记录；旧记录如果曾被退场禁用，
     * 需要先启用再下发新的人脸和门禁权限。
     */
    void enablePerson(String personCode);

    /**
     * 到期退场时从海康 PMAS 中删除或禁用人员。
     *
     * <p>当前业务把到期访客视为不再具备通行资格，因此退场回收会清理人员侧状态。
     */
    void disablePerson(String personCode);

    /**
     * 给已就绪的海康人员绑定人脸。
     *
     * <p>调用前上层会先把自拍照转成海康可接受的 JPG 字节；如果海康提示人员不存在，
     * 上层按平台异步就绪问题延迟重试。
     */
    HikvisionFaceResult addFace(HikvisionAddFaceCommand command);

    /**
     * 退场时删除访客人脸。
     *
     * <p>人脸删除是权限回收后的清理动作；即使人员删除最终完成，也尽量先清掉可识别的人脸资源。
     */
    void deleteFace(String faceGroupIndexCode, String faceIndexCode);

    /**
     * 下发门禁权限配置。
     *
     * <p>审批通过且人员、人脸下发成功后，按访客计划入场/离场时间写入海康 ACPS 权限配置。
     */
    void grantAccess(HikvisionGrantAccessCommand command);

    /**
     * 回收门禁权限配置。
     *
     * <p>访客到期退场时先回收门禁权限，再清理人脸和人员，避免人员资源清理失败时仍可通行。
     */
    void revokeAccess(HikvisionRevokeAccessCommand command);
}
