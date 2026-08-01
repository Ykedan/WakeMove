import type { Metadata } from "next";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "隐私政策",
  description: "了解 WakeMove 醒动如何在设备本地处理闹钟、相机和麦克风数据。",
  alternates: {
    canonical: "https://ykedan.github.io/WakeMove/privacy",
  },
};

const sections = [
  {
    id: "scope",
    label: "适用范围",
    title: "这份政策说明什么",
    content: (
      <>
        <p>
          本政策适用于 WakeMove 醒动 Android 应用及其官方网站。WakeMove 是一款本地优先的动作闹钟，
          无需注册账号，也不提供云端同步服务。
        </p>
        <div className="legal-callout">
          <strong>核心原则</strong>
          <p>能留在手机里的数据，就不离开手机。动作和语音挑战都在设备本地完成。</p>
        </div>
      </>
    ),
  },
  {
    id: "local-data",
    label: "本地数据",
    title: "哪些内容保存在手机上",
    content: (
      <>
        <p>闹钟时间、重复日期、铃声、震动设置、贪睡状态、挑战偏好和响铃历史保存在应用本地。</p>
        <ul>
          <li>相机画面仅在动作挑战期间用于实时姿态判断，不保存、不上传。</li>
          <li>麦克风音频仅交给内置 Vosk 模型进行离线识别，不保存、不上传。</li>
          <li>WakeMove 不收集姓名、手机号、通讯录、精确位置、广告标识符或支付信息。</li>
        </ul>
      </>
    ),
  },
  {
    id: "network",
    label: "联网说明",
    title: "应用何时会联网",
    content: (
      <>
        <p>
          WakeMove 的主要功能无需联网。应用会通过 HTTPS 访问 WakeMove 官方更新文件，检查是否有新版本；
          用户确认后，安装包从 GitHub Releases 下载。
        </p>
        <p>
          更新请求不包含闹钟、相机画面或录音。GitHub Pages、GitHub Releases 及网络运营商可能按照各自规则
          处理 IP 地址、访问时间、设备网络信息等常规连接日志。
        </p>
      </>
    ),
  },
  {
    id: "permissions",
    label: "权限用途",
    title: "为什么需要这些系统权限",
    content: (
      <dl className="legal-permissions">
        <div><dt>相机</dt><dd>识别举手、深蹲等动作挑战，仅在需要时启用。</dd></div>
        <div><dt>麦克风</dt><dd>完成离线语音挑战，仅在需要时启用。</dd></div>
        <div><dt>通知与全屏提醒</dt><dd>在闹钟到点时显示响铃入口，包括锁屏场景。</dd></div>
        <div><dt>精确闹钟与开机恢复</dt><dd>尽可能准时响铃，并在重启或时间变化后恢复计划。</dd></div>
        <div><dt>网络与安装应用</dt><dd>检查、下载并由用户确认安装 WakeMove 更新。</dd></div>
        <div><dt>震动与保持唤醒</dt><dd>按用户设置震动，并在响铃挑战期间保持设备工作。</dd></div>
      </dl>
    ),
  },
  {
    id: "control",
    label: "你的控制权",
    title: "查看、清除与撤回权限",
    content: (
      <>
        <p>
          你可以在 WakeMove 内删除响铃历史和闹钟，也可以在 Android 系统设置中随时撤回相机、麦克风、
          通知等权限。卸载应用会删除其本地数据。
        </p>
        <p>撤回部分权限可能导致相应挑战或闹钟提醒方式不可用，应用会在健康检查中说明影响。</p>
      </>
    ),
  },
  {
    id: "changes-contact",
    label: "变更与联系",
    title: "政策更新与联系我们",
    content: (
      <>
        <p>
          当功能、权限或数据处理方式发生变化时，本页面会同步更新日期。重大变化会在新版说明中提示。
        </p>
        <p>
          对隐私政策有疑问，可以通过
          <a href="https://github.com/Ykedan/WakeMove/issues" target="_blank" rel="noreferrer"> WakeMove GitHub Issues </a>
          联系项目维护者；请勿在公开问题中提交个人敏感信息。
        </p>
      </>
    ),
  },
];

export default function PrivacyPage() {
  return (
    <LegalPage
      eyebrow="PRIVACY / 本地优先"
      title="隐私政策"
      summary="WakeMove 需要相机和麦克风叫醒你，但这些内容不会离开你的手机。这里说明每一项数据与权限的去向。"
      updatedAt="2026 年 8 月 1 日"
      sections={sections}
      alternate={{
        href: "/security",
        label: "安全说明",
        description: "查看正式签名、更新校验与漏洞报告方式。",
      }}
    />
  );
}
