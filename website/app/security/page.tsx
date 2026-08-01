import type { Metadata } from "next";
import updateManifest from "../../public/update.json";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "安全说明",
  description: "了解 WakeMove 醒动的正式签名、更新校验、权限边界与安全问题报告方式。",
  alternates: {
    canonical: "https://ykedan.github.io/WakeMove/security",
  },
};

const signingFingerprint = "8EAAFD35 FDFE2B25 784D29AE ABE91AEF 8B644752 FA2829C6 13D4723D 87EB42CA";

const sections = [
  {
    id: "model",
    label: "安全边界",
    title: "更少的联网面，更清楚的权限边界",
    content: (
      <>
        <p>
          WakeMove 没有账号、广告 SDK、云端数据库或远程控制接口。闹钟、动作判断和语音识别均在本机完成，
          从设计上减少用户数据被集中存储或远程泄露的风险。
        </p>
        <div className="legal-callout legal-callout--security">
          <strong>本地优先不等于绝对安全</strong>
          <p>Android 系统、设备厂商、网络环境、第三方下载渠道和已 Root 设备仍可能带来额外风险。</p>
        </div>
      </>
    ),
  },
  {
    id: "updates",
    label: "可信更新",
    title: "新版安装包如何验证",
    content: (
      <>
        <p>WakeMove 的应用内更新链路采用多层检查：</p>
        <ol>
          <li>更新信息与 APK 只允许来自 WakeMove 指定的 GitHub HTTPS 地址。</li>
          <li>下载完成后，应用会核对发布清单中的 SHA-256。</li>
          <li>Android 安装器要求升级包与已安装应用使用同一正式签名。</li>
          <li>安装动作始终由用户在系统界面中确认。</li>
        </ol>
        <p>
          当前正式版 v{updateManifest.versionName} 安装包 SHA-256：
          <code>{updateManifest.sha256.toUpperCase()}</code>
        </p>
      </>
    ),
  },
  {
    id: "signature",
    label: "正式签名",
    title: "认准 WakeMove 正式签名",
    content: (
      <>
        <p>正式 APK 的签名证书 SHA-256 指纹如下，后续正常更新会继续保持相同签名：</p>
        <code className="legal-fingerprint">{signingFingerprint}</code>
        <p>
          请只从
          <a href="https://github.com/Ykedan/WakeMove/releases" target="_blank" rel="noreferrer"> WakeMove GitHub Releases </a>
          或官网提供的链接下载。来自网盘、群文件和第三方转载站的安装包不属于受信任发布渠道。
        </p>
      </>
    ),
  },
  {
    id: "repository",
    label: "公开与审查",
    title: "代码公开，发布密钥隔离",
    content: (
      <>
        <p>
          WakeMove 源代码公开，任何人都可以检查权限与实现。正式签名文件、签名密码和本机配置不会提交到公开仓库。
        </p>
        <p>
          仓库启用依赖漏洞提醒、自动安全更新、密钥扫描与私密漏洞报告，以便更早发现依赖风险和误提交凭据。
        </p>
      </>
    ),
  },
  {
    id: "report",
    label: "报告漏洞",
    title: "请私密报告安全问题",
    content: (
      <>
        <p>
          如果你发现可能泄露数据、绕过挑战、伪造更新或滥用系统权限的问题，请不要先在公开 Issue、评论区或社交平台披露细节。
        </p>
        <a
          className="legal-report-button"
          href="https://github.com/Ykedan/WakeMove/security/advisories/new"
          target="_blank"
          rel="noreferrer"
        >
          私密报告安全漏洞
          <span aria-hidden="true">↗</span>
        </a>
        <p>请附上影响版本、设备与系统版本、复现步骤和必要日志，但不要提交真实用户隐私数据。</p>
      </>
    ),
  },
  {
    id: "response",
    label: "处理原则",
    title: "我们如何处理报告",
    content: (
      <>
        <p>
          维护者会尽力在 7 天内确认有效报告，并根据影响范围安排修复。修复发布后，再与报告者协调公开说明。
          这是个人维护的开源项目，响应时间可能因问题复杂度有所变化。
        </p>
      </>
    ),
  },
];

export default function SecurityPage() {
  return (
    <LegalPage
      eyebrow="SECURITY / 可信唤醒"
      title="安全说明"
      summary="从离线识别到正式签名，WakeMove 尽量让每一次响铃和每一次更新都能说明来源、验证完整性。"
      updatedAt="2026 年 8 月 1 日"
      sections={sections}
      alternate={{
        href: "/privacy",
        label: "隐私政策",
        description: "了解相机、麦克风与本地数据如何处理。",
      }}
    />
  );
}
