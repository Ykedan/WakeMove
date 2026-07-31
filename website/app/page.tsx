import type { Metadata } from "next";
import { WakeMoveHome } from "./wake-move-home";

export const metadata: Metadata = {
  title: {
    absolute: "WakeMove 醒动｜用动作真正叫醒你的闹钟",
  },
  description:
    "WakeMove 醒动是一款 Android 动作闹钟。响铃后完成举手、深蹲或离线语音挑战，离开床铺，也离开困意。",
  alternates: {
    canonical: "https://ykedan.github.io/WakeMove/",
  },
};

export default function Home() {
  return <WakeMoveHome />;
}
