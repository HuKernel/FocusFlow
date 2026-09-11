# Motion / Sound / Haptic

M0 已集中定义 FocusMotion 的 80/140/220/320/600ms token，Reduced Motion 返回零移动时长，逻辑有测试。
SoundController/HapticController 为 commonMain 接口，事件覆盖 PRD；未实现播放或振动，不宣称已经具备反馈。
M3 接入统一 easing、平台 reduced-motion 偏好、重要状态动效和音触反馈。
Sound Effects 与 White Noise 分开控制音量；后台白噪音使用 Media3 MediaSessionService，不混入 timer/sync FGS。
