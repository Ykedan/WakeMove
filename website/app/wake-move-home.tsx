"use client";

import Image from "next/image";
import { useEffect, useRef } from "react";
import updateManifest from "../public/update.json";

const CURRENT_VERSION = updateManifest.versionName;
const DOWNLOAD_URL = updateManifest.downloadUrl;
const GITHUB_URL = "https://github.com/Ykedan/WakeMove";
const PUBLIC_BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH ?? "";

function ArrowDown() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3v13m0 0 5-5m-5 5-5-5M5 21h14" />
    </svg>
  );
}

function OrbitMark({ light = false }: { light?: boolean }) {
  return (
    <span className={`orbit-mark${light ? " orbit-mark--light" : ""}`} aria-hidden="true">
      <span />
    </span>
  );
}

function ChallengeIcon({ type }: { type: "motion" | "voice" | "sound" }) {
  if (type === "motion") {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <circle cx="16" cy="7" r="3" />
        <path d="M16 11v8m0-6-7 4m7-4 7 4m-7 2-6 8m6-8 6 8" />
      </svg>
    );
  }
  if (type === "voice") {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <rect x="11" y="5" width="10" height="16" rx="5" />
        <path d="M7 16a9 9 0 0 0 18 0M16 25v4m-5 0h10" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <path d="M6 19h5l6 5V8l-6 5H6v6Z" />
      <path d="M22 12a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14" />
    </svg>
  );
}

export function WakeMoveHome() {
  const phoneRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const revealItems = document.querySelectorAll<HTMLElement>("[data-reveal]");
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.14 },
    );
    revealItems.forEach((item) => observer.observe(item));
    return () => observer.disconnect();
  }, []);

  function tiltPhone(event: React.PointerEvent<HTMLDivElement>) {
    if (!phoneRef.current || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const rect = phoneRef.current.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width - 0.5;
    const y = (event.clientY - rect.top) / rect.height - 0.5;
    phoneRef.current.style.setProperty("--tilt-x", `${y * -5}deg`);
    phoneRef.current.style.setProperty("--tilt-y", `${x * 7}deg`);
  }

  function resetTilt() {
    phoneRef.current?.style.setProperty("--tilt-x", "0deg");
    phoneRef.current?.style.setProperty("--tilt-y", "0deg");
  }

  return (
    <main>
      <nav className="site-nav" aria-label="主导航">
        <a className="brand" href="#top" aria-label="WakeMove 醒动首页">
          <OrbitMark />
          <span>WAKE<em>MOVE</em></span>
          <b>醒动</b>
        </a>
        <div className="nav-links">
          <a href="#how">如何唤醒</a>
          <a href="#screens">真机界面</a>
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">
            GitHub
          </a>
        </div>
        <a className="nav-download" href={DOWNLOAD_URL}>
          下载 Android 版
          <ArrowDown />
        </a>
      </nav>

      <section className="hero" id="top">
        <div className="hero-grid" aria-hidden="true" />
        <div className="hero-orbit" aria-hidden="true">
          <span className="hero-orbit__dot" />
          <span className="hero-orbit__echo" />
        </div>

        <div className="hero-copy">
          <div className="eyebrow hero-intro">
            <span>ANDROID 动作闹钟</span>
            <i />
            <span>V{CURRENT_VERSION}</span>
          </div>
          <h1>
            叫醒你的，
            <br />
            <span>不只是铃声。</span>
          </h1>
          <p className="hero-lead">
            响铃后完成动作或语音挑战。
            <br />
            离开床铺，也离开困意。
          </p>
          <div className="hero-actions">
            <a className="primary-action" href={DOWNLOAD_URL}>
              <span className="android-glyph" aria-hidden="true">
                ↓
              </span>
              <span>
                <small>免费下载</small>
                Android APK
              </span>
            </a>
            <a className="text-action" href="#how">
              看看它如何叫醒你
              <span aria-hidden="true">↘</span>
            </a>
          </div>
          <p className="download-note">适用于 Android 10 及以上 · 安装包约 196 MB</p>
        </div>

        <div
          className="hero-device-stage"
          onPointerMove={tiltPhone}
          onPointerLeave={resetTilt}
          aria-label="WakeMove 醒动应用首页预览"
        >
          <span className="stage-label stage-label--one">07:30 / 闹钟已就绪</span>
          <span className="stage-label stage-label--two">动作挑战 / ACTIVE</span>
          <div className="phone-glow" aria-hidden="true" />
          <div className="phone phone--hero" ref={phoneRef}>
            <span className="phone-speaker" aria-hidden="true" />
            <Image
              src={`${PUBLIC_BASE_PATH}/screens/home.png`}
              alt="WakeMove 醒动 App 闹钟首页"
              width={1080}
              height={2400}
              priority
            />
          </div>
        </div>

        <a className="scroll-cue" href="#how" aria-label="向下浏览">
          <span>SCROLL TO WAKE</span>
          <i />
        </a>
      </section>

      <section className="signal-strip" aria-label="产品特点">
        <div>
          <span>01</span>
          <strong>离线可用</strong>
          <p>本地语音识别，无需联网</p>
        </div>
        <div>
          <span>02</span>
          <strong>全屏响铃</strong>
          <p>亮屏、锁屏都不轻易错过</p>
        </div>
        <div>
          <span>03</span>
          <strong>真正清醒</strong>
          <p>用身体完成最后一步</p>
        </div>
      </section>

      <section className="how-section" id="how">
        <div className="section-heading" data-reveal>
          <span className="kicker">WAKE SEQUENCE / 唤醒流程</span>
          <h2>
            从听见闹钟，
            <br />
            到<span>真的起床。</span>
          </h2>
        </div>

        <div className="wake-sequence">
          <div className="sequence-line" aria-hidden="true">
            <i />
          </div>
          <article data-reveal>
            <span className="sequence-number">01</span>
            <div className="sequence-time">07:30</div>
            <div>
              <h3>闹钟准时响起</h3>
              <p>全屏提醒、铃声与震动一起工作，把第一声叫醒变得可靠。</p>
            </div>
          </article>
          <article data-reveal>
            <span className="sequence-number">02</span>
            <div className="sequence-action">
              <ChallengeIcon type="motion" />
            </div>
            <div>
              <h3>接受一个清醒挑战</h3>
              <p>举起双手、完成深蹲，或准确说出一句短语。赖床不再只靠意志力。</p>
            </div>
          </article>
          <article data-reveal>
            <span className="sequence-number">03</span>
            <div className="sequence-sun" aria-hidden="true" />
            <div>
              <h3>带着清醒开始今天</h3>
              <p>挑战完成，响铃才会结束。下一步，就是把今天过好。</p>
            </div>
          </article>
        </div>
      </section>

      <section className="screens-section" id="screens">
        <div className="screens-copy" data-reveal>
          <span className="kicker kicker--light">REAL PRODUCT / 真实界面</span>
          <h2>
            夜里安静，
            <br />
            醒来<span>明确。</span>
          </h2>
          <p>
            没有多余的装饰，也没有复杂路径。每个页面只在该出现的时候，帮你完成一件事。
          </p>
          <div className="screen-index">
            <span>01 初次见面</span>
            <span>02 安排明天</span>
            <span>03 准备就绪</span>
          </div>
        </div>

        <div className="device-gallery" data-reveal>
          <div className="phone phone--side phone--left">
            <span className="phone-speaker" aria-hidden="true" />
            <Image
              src={`${PUBLIC_BASE_PATH}/screens/onboarding.png`}
              alt="WakeMove 醒动首次进入页面"
              width={1080}
              height={2400}
            />
          </div>
          <div className="phone phone--center">
            <span className="phone-speaker" aria-hidden="true" />
            <Image
              src={`${PUBLIC_BASE_PATH}/screens/home.png`}
              alt="WakeMove 醒动闹钟列表页面"
              width={1080}
              height={2400}
            />
          </div>
          <div className="phone phone--side phone--right">
            <span className="phone-speaker" aria-hidden="true" />
            <Image
              src={`${PUBLIC_BASE_PATH}/screens/permissions.png`}
              alt="WakeMove 醒动权限引导页面"
              width={1080}
              height={2400}
            />
          </div>
        </div>
      </section>

      <section className="challenge-section">
        <div className="section-heading section-heading--compact" data-reveal>
          <span className="kicker">BUILT FOR REAL MORNINGS / 为真实早晨设计</span>
          <h2>
            你选择闹钟，
            <br />
            也选择<span>醒来的方式。</span>
          </h2>
        </div>
        <div className="challenge-grid">
          <article data-reveal>
            <div className="challenge-icon">
              <ChallengeIcon type="motion" />
            </div>
            <span>01 / 动作识别</span>
            <h3>让身体先醒来</h3>
            <p>通过相机识别举手与深蹲动作，完成以后才能结束响铃。</p>
          </article>
          <article data-reveal>
            <div className="challenge-icon">
              <ChallengeIcon type="voice" />
            </div>
            <span>02 / 离线语音</span>
            <h3>让大脑跟上来</h3>
            <p>内置离线识别能力，没有系统语音服务也能完成短句挑战。</p>
          </article>
          <article data-reveal>
            <div className="challenge-icon">
              <ChallengeIcon type="sound" />
            </div>
            <span>03 / 自定义唤醒</span>
            <h3>以舒服的方式开始</h3>
            <p>选择铃声、震动节奏与强度，让每个清晨有自己的声音。</p>
          </article>
        </div>
      </section>

      <section className="privacy-section">
        <div className="privacy-orbit" aria-hidden="true">
          <i />
        </div>
        <div className="privacy-copy" data-reveal>
          <OrbitMark light />
          <span className="kicker kicker--light">LOCAL FIRST / 本地优先</span>
          <h2>你的清晨，<br />留在你的手机里。</h2>
          <p>
            动作判断与语音识别在设备本地完成。WakeMove 不需要账号，也不依赖云端才能叫醒你。
          </p>
        </div>
        <div className="privacy-code" data-reveal aria-label="本地能力说明">
          <div>
            <span>CAMERA</span>
            <b>LOCAL MOTION</b>
            <i>仅用于动作挑战</i>
          </div>
          <div>
            <span>VOICE</span>
            <b>OFFLINE VOSK</b>
            <i>识别无需联网</i>
          </div>
          <div>
            <span>ACCOUNT</span>
            <b>NOT REQUIRED</b>
            <i>打开即可使用</i>
          </div>
        </div>
      </section>

      <section className="final-cta">
        <div className="final-orbit" aria-hidden="true">
          <i />
        </div>
        <div data-reveal>
          <span className="kicker">TOMORROW STARTS TONIGHT</span>
          <h2>
            明早见。
            <br />
            <span>这次，真的醒来。</span>
          </h2>
          <a className="primary-action primary-action--large" href={DOWNLOAD_URL}>
            <span className="android-glyph" aria-hidden="true">↓</span>
            <span>
              <small>WakeMove v{CURRENT_VERSION}</small>
              下载 Android APK
            </span>
          </a>
          <p>免费下载 · Android 10+ · GitHub Release 安全直链</p>
        </div>
      </section>

      <footer>
        <a className="brand brand--footer" href="#top">
          <OrbitMark />
          <span>WAKE<em>MOVE</em></span>
          <b>醒动</b>
        </a>
        <p>不只叫醒你，还让你真正醒来。</p>
        <div>
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">GitHub</a>
          <a href={`${GITHUB_URL}/releases`} target="_blank" rel="noreferrer">版本记录</a>
          <a href={`${GITHUB_URL}#readme`} target="_blank" rel="noreferrer">项目说明</a>
        </div>
        <small>© 2026 WakeMove 醒动</small>
      </footer>
    </main>
  );
}
