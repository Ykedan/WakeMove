import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(
    "https://wakemove-xingdong.cloudy-flame-6804.chatgpt.site",
  ),
  title: {
    default: "WakeMove 醒动",
    template: "%s｜WakeMove 醒动",
  },
  description: "不只叫醒你，还让你真正醒来。",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
  openGraph: {
    type: "website",
    locale: "zh_CN",
    siteName: "WakeMove 醒动",
    title: "WakeMove 醒动｜不只叫醒你，还让你真正醒来",
    description: "响铃后完成动作或语音挑战，离开困意，再开始今天。",
    images: [{ url: "/og.png", width: 1200, height: 630 }],
  },
};

export const viewport: Viewport = {
  themeColor: "#0D1324",
  colorScheme: "dark light",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
